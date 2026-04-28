# # -*- coding: utf-8 -*-
# """
# Embedding and Vector Search Module using BGE-M3 and Pinecone
# """

# from typing import List, Dict, Any, Optional
# from pinecone import Pinecone, ServerlessSpec
# from FlagEmbedding import BGEM3FlagModel
# from sqlmodel.ext.asyncio.session import AsyncSession
# from sqlmodel import select
# from app.models import Book, Chapter, ChapterContent
# from app.core.config import settings
# from app.core.db import async_session_factory


# class EmbeddingService:
#     """Service for handling text embeddings and vector operations"""
    
#     def __init__(self, index_name: str = "hybrid-spilt"):
#         """
#         Initialize the embedding service
        
#         Args:
#             api_key: Pinecone API key (defaults to settings.PINECONE_API_KEY)
#             index_name: Name of the Pinecone index
#         """
#         self.pc = Pinecone(api_key=settings.PINECONE_API_KEY)
#         self.index_name = index_name
#         self.model = BGEM3FlagModel('BAAI/bge-m3', use_fp16=True)
#         self.index = None
        
#     def create_index_if_not_exists(self):
#         """Create Pinecone index if it doesn't exist"""
#         if not self.pc.has_index(self.index_name):
#             self.pc.create_index(
#                 name=self.index_name,
#                 vector_type="dense",
#                 dimension=1024,
#                 metric="dotproduct",
#                 spec=ServerlessSpec(
#                     cloud="aws",
#                     region="us-east-1"
#                 )
#             )
    
#     def connect_to_index(self):
#         """Connect to the Pinecone index"""
#         index_description = self.pc.describe_index(self.index_name)
#         index_host = index_description.host
#         self.index = self.pc.Index(host=index_host)
        
#     def split_with_overlap(
#         self, 
#         data: List[Dict], 
#         target_size: int = 2000, 
#         overlap_size: int = 400
#     ) -> List[Dict]:
#         """
#         Split text data into chunks with overlap
        
#         Args:
#             data: List of chapter data dictionaries
#             target_size: Target size for each chunk in characters
#             overlap_size: Overlap size between chunks in characters
            
#         Returns:
#             List of chunked data dictionaries
#         """
#         new_data = []

#         for item in data:
#             chapter_id = item.get("chapter_id")
#             chapter_index = item.get("chapter_index")
#             full_text = item.get("chunk_text", "")
#             full_text = full_text.replace('\r\n', '\n')


#             # Tách thành danh sách các paragraph dựa trên \n\n
#             paragraphs = [p.strip() for p in full_text.split('\n\n') if p.strip()]

#             start_idx = 0
#             sub_index = 1

#             while start_idx < len(paragraphs):
#                 current_chunk_paras = []
#                 current_length = 0

#                 # 1. Thu thập các đoạn văn cho đến khi đạt target_size
#                 end_idx = start_idx
#                 while end_idx < len(paragraphs):
#                     para = paragraphs[end_idx]
#                     # Nếu thêm đoạn này vào mà vượt quá size (trừ khi là đoạn đầu tiên của chunk)
#                     if current_length + len(para) > target_size and current_length > 0:
#                         break
#                     current_chunk_paras.append(para)
#                     current_length += len(para) + 2  # +2 cho dấu \n\n
#                     end_idx += 1

#                 # Lưu chunk hiện tại
#                 new_data.append({
#                     "chapter_index": chapter_index,
#                     "chunk_text": "\n\n".join(current_chunk_paras),
#                     "book_id": item["book_id"],
#                     "book_name": item["book_name"],
#                     "chapter_name": item["chapter_name"],
#                     "chapter_id": item["chapter_id"],
#                     "chunk_index": sub_index
#                 })

#                 if end_idx >= len(paragraphs):
#                     break

#                 # 2. Tính toán điểm bắt đầu cho chunk tiếp theo (Overlap)
#                 # Lùi start_idx lại sao cho tổng độ dài các đoạn lùi lại xấp xỉ overlap_size
#                 temp_overlap_len = 0
#                 new_start_idx = end_idx
#                 while new_start_idx > start_idx:
#                     para_len = len(paragraphs[new_start_idx - 1]) + 2
#                     if temp_overlap_len + para_len > overlap_size:
#                         break
#                     temp_overlap_len += para_len
#                     new_start_idx -= 1

#                 # Đảm bảo start_idx luôn tiến lên để tránh vòng lặp vô tận
#                 start_idx = max(new_start_idx, start_idx + 1)
#                 sub_index += 1

#         return new_data
    
#     def get_hybrid_embedding(self, texts: List[str]) -> Dict[str, Any]:
#         """
#         Generate hybrid embeddings (dense + sparse) for texts
        
#         Args:
#             texts: List of text strings to embed
            
#         Returns:
#             Dictionary with dense_vecs and lexical_weights
#         """
#         output = self.model.encode(
#             texts,
#             return_dense=True,
#             return_sparse=True,
#         )
#         return output
    
#     def prepare_records(self, data: List[Dict], embeddings: Dict[str, Any]) -> List[Dict]:
#         """
#         Prepare records for Pinecone upsert
        
#         Args:
#             data: List of chunk data
#             embeddings: Output from get_hybrid_embedding
            
#         Returns:
#             List of formatted records for Pinecone
#         """
#         dense_embeddings = embeddings["dense_vecs"]
#         sparse_embeddings = embeddings["lexical_weights"]
        
#         records = []
#         for i, d in enumerate(data):
#             dense_vec = dense_embeddings[i]
#             sparse_dict = sparse_embeddings[i]

#             records.append({
#                 "id": str(d['chapter_id']) + '_' + str(d["chunk_index"]),
#                 "values": dense_vec.tolist(),
#                 "sparse_values": {
#                     "indices": [int(k) for k in sparse_dict.keys()],
#                     "values": [float(v) for v in sparse_dict.values()]
#                 },
#                 "metadata": {
#                     "chapter_name": d['chapter_name'],
#                     "book_name": d['book_name'],
#                     "text": d['chunk_text'],
#                     "book_id": d['book_id'],
#                     "index": d['chapter_index'],
#                     "chapter_id": d['chapter_id']
#                 }
#             })
        
#         return records
    
#     @staticmethod
#     def chunks(iterable: List, batch_size: int = 100):
#         """Yield successive n-sized chunks from iterable"""
#         for i in range(0, len(iterable), batch_size):
#             yield iterable[i:i + batch_size]
    
#     def upsert_records(
#         self, 
#         records: List[Dict], 
#         namespace: str = "mytruyen", 
#         batch_size: int = 50
#     ):
#         """
#         Upsert records to Pinecone in batches
        
#         Args:
#             records: List of prepared records
#             namespace: Pinecone namespace
#             batch_size: Number of records per batch
#         """
#         if not self.index:
#             raise ValueError("Index not connected. Call connect_to_index() first.")
        
#         print(f"Total records to upsert: {len(records)}")
        
#         for i, batch in enumerate(self.chunks(records, batch_size=batch_size)):
#             try:
#                 self.index.upsert(vectors=batch, namespace=namespace)
#                 print(f"Successfully upserted batch {i+1} (Records {i*batch_size} to {i*batch_size + len(batch)})")
#             except Exception as e:
#                 print(f"Error at batch {i+1}: {e}")
    
#     def search(
#         self, 
#         query_text: str, 
#         namespace: str = "mytruyen", 
#         top_k: int = 30,
#         rerank: bool = True,
#         rerank_top_n: int = 10
#     ) -> List[Dict]:
#         """
#         Search for similar texts using hybrid search with optional reranking
        
#         Args:
#             query_text: Text to search for
#             namespace: Pinecone namespace
#             top_k: Number of results to retrieve
#             rerank: Whether to apply reranking
#             rerank_top_n: Number of top results after reranking
            
#         Returns:
#             List of search results
#         """
#         if not self.index:
#             raise ValueError("Index not connected. Call connect_to_index() first.")
        
#         # Generate query embeddings
#         query_output = self.model.encode(
#             query_text,
#             return_dense=True,
#             return_sparse=True,
#         )
        
#         # Convert to Pinecone format
#         dense_vector = query_output['dense_vecs'].tolist()
#         sparse_dict = query_output['lexical_weights']
#         sparse_vector = {
#             "indices": [int(k) for k in sparse_dict.keys()],
#             "values": [float(v) for v in sparse_dict.values()]
#         }
        
#         # Query Pinecone
#         results = self.index.query(
#             namespace=namespace,
#             vector=dense_vector,
#             sparse_vector=sparse_vector,
#             top_k=top_k,
#             include_metadata=True
#         )
        
#         # Prepare documents for reranking
#         documents = [
#             {
#                 "id": match["id"],
#                 "text": match["metadata"]["text"],
#                 "chapter_id": match["metadata"]["chapter_id"],
#                 "book_id": match["metadata"]["book_id"],
#                 "chapter_index": match["metadata"]["index"],
#                 "book_name": match["metadata"]["book_name"],
#                 "chapter_name": match["metadata"]["chapter_name"]
#             }
#             for match in results["matches"]
#         ]
        
#         # Apply reranking if requested
#         if rerank and documents:
#             rerank_results = self.pc.inference.rerank(
#                 model="bge-reranker-v2-m3",
#                 query=query_text,
#                 documents=documents,
#                 top_n=rerank_top_n,
#                 return_documents=True,
#                 rank_fields=["text"]
#             )
#             return rerank_results.data
        
#         return documents
    
#     async def fetch_chapters_from_db(
#         self, 
#         book_id: Optional[str] = None,
#         limit: Optional[int] = None
#     ) -> List[Dict]:
#         """
#         Fetch chapters data from PostgreSQL database
        
#         Args:
#             book_id: Optional book ID to filter chapters
#             limit: Optional limit on number of chapters to fetch
            
#         Returns:
#             List of chapter data dictionaries in the format needed for processing
#         """
#         async with async_session_factory() as session:
#             # Build query
#             stmt = (
#                 select(Chapter, ChapterContent, Book)
#                 .join(ChapterContent, Chapter.id == ChapterContent.chapter_id)
#                 .join(Book, Chapter.book_id == Book.id)
#                 .where(Chapter.published == True)
#             )
            
#             if book_id:
#                 stmt = stmt.where(Chapter.book_id == book_id)
            
#             if limit:
#                 stmt = stmt.limit(limit)
            
#             # Execute query
#             result = await session.exec(stmt)
#             rows = result.all()
            
#             # Format data
#             data = []
#             for chapter, chapter_content, book in rows:
#                 data.append({
#                     "chapter_id": str(chapter.id),
#                     "chapter_index": chapter.index,
#                     "chunk_text": chapter_content.content,
#                     "book_id": str(book.id),
#                     "book_name": book.name,
#                     "chapter_name": chapter.name
#                 })
            
#             print(f"Fetched {len(data)} chapters from database")
#             return data
    
#     async def load_and_process_data_from_db(
#         self, 
#         book_id: Optional[str] = None,
#         limit: Optional[int] = None
#     ) -> List[Dict]:
#         """
#         Load data from database and process it
        
#         Args:
#             book_id: Optional book ID to filter chapters
#             limit: Optional limit on number of chapters to fetch
            
#         Returns:
#             List of processed chunks
#         """
#         # Fetch data from database
#         data = await self.fetch_chapters_from_db(book_id=book_id, limit=limit)
        
#         # Split with overlap
#         final_result = self.split_with_overlap(data, target_size=3000, overlap_size=400)
#         print(f"Created {len(final_result)} chunks with overlap.")
        
#         return final_result
    
#     async def embed_and_upload_data_from_db(
#         self, 
#         book_id: Optional[str] = None,
#         limit: Optional[int] = None,
#         namespace: str = "mytruyen", 
#         batch_size: int = 50
#     ):
#         """
#         Complete pipeline: fetch from DB, embed data and upload to Pinecone
        
#         Args:
#             book_id: Optional book ID to filter chapters
#             limit: Optional limit on number of chapters to fetch
#             namespace: Pinecone namespace
#             batch_size: Batch size for uploading
#         """
#         # Load and process data from database
#         data = await self.load_and_process_data_from_db(book_id=book_id, limit=limit)
        
#         # Generate embeddings
#         texts = [d["chunk_text"] for d in data]
#         embeddings = self.get_hybrid_embedding(texts)
        
#         # Prepare records
#         records = self.prepare_records(data, embeddings)
        
#         # Upload to Pinecone
#         self.upsert_records(records, namespace=namespace, batch_size=batch_size)


# # Example usage
# if __name__ == "__main__":
#     import asyncio
    
#     async def main():
#         # Initialize service
#         service = EmbeddingService()
        
#         # Create index and connect
#         service.create_index_if_not_exists()
#         service.connect_to_index()
        
#         # Load and process data from database
#         print("=== Fetching data from database ===")
#         await service.embed_and_upload_data_from_db(
#             book_id=None,  # None = all books, or specify a book_id
#             limit=None,      # Limit number of chapters for testing
#             namespace="mytruyen",
#             batch_size=50
#         )
        
#         # Search example
#         query_text = "Màu đỏ thẫm Thủy Long, đã đem trên người Cuồng Nhận Lang Nhân trong suốt áo giáp xuyên qua!"
#         results = service.search(query_text, top_k=30, rerank=True, rerank_top_n=10)
        
#         print("\n--- RERANKED RESULTS ---")
#         for row in results:
#             print(row)
    
#     asyncio.run(main())

