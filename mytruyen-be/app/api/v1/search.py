from fastapi import APIRouter, Request, UploadFile, File, HTTPException, status
from pydantic import BaseModel
from app.schema.response import Response, ResponseList
# from app.utilities.audio_transcription import AudioTranscriber
# from app.utilities.youtube_downloader import YouTubeAudioDownloader

router = APIRouter(prefix="/search", tags=["search"])


class YouTubeSearchRequest(BaseModel):
    url: str
    language: str = "vi"

@router.get("", response_model=ResponseList[dict])
async def search_stories(request: Request, query_text: str):
    raise HTTPException(
        status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
        detail="Tính năng này tạm thời bị vô hiệu hóa."
    )


    print("Search query received:", query_text)
    model = request.app.state.model
    index = request.app.state.pc_index
    pc = request.app.state.pc
    print("Model and index accessed from app state.")
    query_output = model.encode(
        query_text,
        return_dense=True,
        return_sparse=True,
    )
    print("Query encoded.")

    dense_vector = query_output['dense_vecs'].tolist()
    sparse_dict = query_output['lexical_weights']
    sparse_vector = {
        "indices": [int(k) for k in sparse_dict.keys()],
        "values": [float(v) for v in sparse_dict.values()]
    }
    print("Dense and sparse vectors prepared.")
    results = index.query(
        namespace="mytruyen",
        vector=dense_vector,
        sparse_vector=sparse_vector,
        top_k=30,
        include_metadata=True
    )
    print("Initial search query executed.")

    documents = [
        {
        "id": match["id"], 
        "text": match["metadata"]["text"], 
        "chapter_id": match["metadata"]["chapter_id"],
        "book_id": match["metadata"]["book_id"],
        "chapter_index": match["metadata"]["index"],
        "book_name": match["metadata"]["book_name"],    
        "chapter_name": match["metadata"]["chapter_name"]
        } 
        for match in results["matches"]
    ]
    print(f"Retrieved {len(documents)} documents from initial search.")

    rerank_results = pc.inference.rerank(
        model="bge-reranker-v2-m3", 
        query=query_text,
        documents=documents,
        top_n=10,
        return_documents=True,
        rank_fields=["text"]
    )
    print("Reranking completed.")

    final_output = []
    for hit in rerank_results.data:
        doc = hit.document
        final_output.append({
            "id": doc.get("id"),
            "score": float(hit.score),
            "text": doc.get("text"),
            "metadata": {
                "book_id": doc.get("book_id"),
                "chapter_id": doc.get("chapter_id"),
                "chapter_index": doc.get("chapter_index")
            }
        })
    print(f"Final output prepared with {len(final_output)} results.")

    return Response(
        status_code=200,
        success=True,
        message="Search completed successfully",
        data=final_output
    )


@router.post("/audio", response_model=ResponseList[dict])
async def search_stories_by_audio(
    request: Request, 
    audio_file: UploadFile = File(...),
    language: str = "vi"
):
    raise HTTPException(
        status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
        detail="Tính năng này tạm thời bị vô hiệu hóa."
    )
    """
    Endpoint để search truyện bằng audio.
    
    Args:
        audio_file: File audio (hỗ trợ các format: mp3, wav, m4a, ogg, flac, webm)
        language: Ngôn ngữ của audio (mặc định: vi - tiếng Việt)
    
    Returns:
        Kết quả search tương tự như text search
    """
    # Kiểm tra file extension
    allowed_extensions = {".mp3", ".wav", ".m4a", ".ogg", ".flac", ".webm", ".mp4"}
    file_extension = "." + audio_file.filename.split(".")[-1].lower()
    
    if file_extension not in allowed_extensions:
        raise HTTPException(
            status_code=400,
            detail=f"File format không được hỗ trợ. Chỉ chấp nhận: {', '.join(allowed_extensions)}"
        )
    
    print(f"Audio file received: {audio_file.filename}")
    
    try:
        # Đọc audio file
        audio_bytes = await audio_file.read()
        print(f"Audio file size: {len(audio_bytes)} bytes")
        
        # Lấy Whisper model từ app state và tạo transcriber
        whisper_model = request.app.state.whisper_model
        transcriber = AudioTranscriber(whisper_model)
        query_text = await transcriber.transcribe_from_bytes(
            audio_bytes, 
            audio_file.filename,
            language=language
        )
        print(f"Transcribed text: {query_text}")
        
        if not query_text or len(query_text.strip()) == 0:
            raise HTTPException(
                status_code=400,
                detail="Không thể nhận diện được giọng nói từ file audio"
            )
        
        # Sử dụng logic search như bình thường
        model = request.app.state.model
        index = request.app.state.pc_index
        pc = request.app.state.pc
        
        query_output = model.encode(
            query_text,
            return_dense=True,
            return_sparse=True,
        )
        
        dense_vector = query_output['dense_vecs'].tolist()
        sparse_dict = query_output['lexical_weights']
        sparse_vector = {
            "indices": [int(k) for k in sparse_dict.keys()],
            "values": [float(v) for v in sparse_dict.values()]
        }
        
        results = index.query(
            namespace="mytruyen",
            vector=dense_vector,
            sparse_vector=sparse_vector,
            top_k=30,
            include_metadata=True
        )
        
        documents = [
            {
                "id": match["id"], 
                "text": match["metadata"]["text"], 
                "chapter_id": match["metadata"]["chapter_id"],
                "book_id": match["metadata"]["book_id"],
                "chapter_index": match["metadata"]["index"],
                "book_name": match["metadata"]["book_name"],    
                "chapter_name": match["metadata"]["chapter_name"]
            } 
            for match in results["matches"]
        ]
        
        rerank_results = pc.inference.rerank(
            model="bge-reranker-v2-m3", 
            query=query_text,
            documents=documents,
            top_n=10,
            return_documents=True,
            rank_fields=["text"]
        )
        
        final_output = []
        for hit in rerank_results.data:
            doc = hit.document
            final_output.append({
                "id": doc.get("id"),
                "score": float(hit.score),
                "text": doc.get("text"),
                "metadata": {
                    "book_id": doc.get("book_id"),
                    "chapter_id": doc.get("chapter_id"),
                    "chapter_index": doc.get("chapter_index")
                }
            })
        
        return Response(
            status_code=200,
            success=True,
            message=f"Search completed successfully. Transcribed text: '{query_text}'",
            data=final_output
        )
        
    except HTTPException:
        raise
    except Exception as e:
        print(f"Error processing audio search: {str(e)}")
        raise HTTPException(
            status_code=500,
            detail=f"Lỗi khi xử lý audio: {str(e)}"
        )


@router.post("/youtube", response_model=ResponseList[dict])
async def search_stories_by_youtube(
    request: Request,
    youtube_request: YouTubeSearchRequest
):
    raise HTTPException(
        status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
        detail="Tính năng này tạm thời bị vô hiệu hóa."
    )
    """
    Endpoint để search truyện bằng YouTube URL.
    
    Args:
        url: YouTube URL (ví dụ: https://www.youtube.com/watch?v=...)
        language: Ngôn ngữ của audio trong video (mặc định: vi - tiếng Việt)
    
    Returns:
        Kết quả search tương tự như text search
    """
    print(f"YouTube URL received: {youtube_request.url}")
    
    try:
        # Tải audio từ YouTube
        youtube_downloader = YouTubeAudioDownloader()
        
        # Validate YouTube URL
        if not youtube_downloader.is_valid_youtube_url(youtube_request.url):
            raise HTTPException(
                status_code=400,
                detail="URL không phải là YouTube URL hợp lệ"
            )
        
        # Tải audio từ YouTube
        print("Downloading audio from YouTube...")
        audio_bytes, filename = await youtube_downloader.download_audio(
            youtube_request.url, 
            youtube_request.language
        )
        print(f"Audio downloaded: {filename}, size: {len(audio_bytes)} bytes")
        
        # Transcribe audio thành text
        whisper_model = request.app.state.whisper_model
        transcriber = AudioTranscriber(whisper_model)
        query_text = await transcriber.transcribe_from_bytes(
            audio_bytes, 
            filename,
            language=youtube_request.language
        )
        print(f"Transcribed text: {query_text}")
        
        if not query_text or len(query_text.strip()) == 0:
            raise HTTPException(
                status_code=400,
                detail="Không thể nhận diện được giọng nói từ video YouTube"
            )
        
        # Sử dụng logic search như bình thường
        model = request.app.state.model
        index = request.app.state.pc_index
        pc = request.app.state.pc
        
        query_output = model.encode(
            query_text,
            return_dense=True,
            return_sparse=True,
        )
        
        dense_vector = query_output['dense_vecs'].tolist()
        sparse_dict = query_output['lexical_weights']
        sparse_vector = {
            "indices": [int(k) for k in sparse_dict.keys()],
            "values": [float(v) for v in sparse_dict.values()]
        }
        
        results = index.query(
            namespace="mytruyen",
            vector=dense_vector,
            sparse_vector=sparse_vector,
            top_k=30,
            include_metadata=True
        )
        
        documents = [
            {
                "id": match["id"], 
                "text": match["metadata"]["text"], 
                "chapter_id": match["metadata"]["chapter_id"],
                "book_id": match["metadata"]["book_id"],
                "chapter_index": match["metadata"]["index"],
                "book_name": match["metadata"]["book_name"],    
                "chapter_name": match["metadata"]["chapter_name"]
            } 
            for match in results["matches"]
        ]
        
        rerank_results = pc.inference.rerank(
            model="bge-reranker-v2-m3", 
            query=query_text,
            documents=documents,
            top_n=10,
            return_documents=True,
            rank_fields=["text"]
        )
        
        final_output = []
        for hit in rerank_results.data:
            doc = hit.document
            final_output.append({
                "id": doc.get("id"),
                "score": float(hit.score),
                "text": doc.get("text"),
                "metadata": {
                    "book_id": doc.get("book_id"),
                    "chapter_id": doc.get("chapter_id"),
                    "chapter_index": doc.get("chapter_index")
                }
            })
        
        return Response(
            status_code=200,
            success=True,
            message=f"Search completed successfully. Transcribed text: '{query_text}'",
            data=final_output
        )
        
    except HTTPException:
        raise
    except Exception as e:
        print(f"Error processing YouTube search: {str(e)}")
        raise HTTPException(
            status_code=500,
            detail=f"Lỗi khi xử lý YouTube URL: {str(e)}"
        )

