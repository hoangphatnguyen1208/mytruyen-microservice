# import os
# import tempfile
# from faster_whisper import WhisperModel
# from pathlib import Path


# class AudioTranscriber:
#     """
#     Service để chuyển đổi audio thành text sử dụng faster-whisper
#     """
    
#     def __init__(self, model: WhisperModel):
#         """
#         Khởi tạo AudioTranscriber với Whisper model đã được load
        
#         Args:
#             model: WhisperModel instance đã được load trước đó
#         """
#         self.model = model
    
#     async def transcribe_audio(self, audio_file_path: str, language: str = "vi") -> str:
#         """
#         Chuyển đổi audio file thành text
        
#         Args:
#             audio_file_path: Đường dẫn tới file audio
#             language: Ngôn ngữ của audio (mặc định là tiếng Việt)
            
#         Returns:
#             str: Text đã được transcribe từ audio
#         """
#         try:
#             # Transcribe audio
#             segments, info = self.model.transcribe(
#                 audio_file_path, 
#                 language=language,
#                 beam_size=5,
#                 vad_filter=True,  # Voice Activity Detection để loại bỏ im lặng
#                 vad_parameters=dict(min_silence_duration_ms=500)
#             )
            
#             # Kết hợp tất cả segments thành một text
#             transcribed_text = " ".join([segment.text for segment in segments])
            
#             return transcribed_text.strip()
            
#         except Exception as e:
#             raise Exception(f"Lỗi khi transcribe audio: {str(e)}")
    
#     async def transcribe_from_bytes(self, audio_bytes: bytes, filename: str, language: str = "vi") -> str:
#         """
#         Chuyển đổi audio từ bytes thành text
        
#         Args:
#             audio_bytes: Audio data dạng bytes
#             filename: Tên file (để xác định extension)
#             language: Ngôn ngữ của audio
            
#         Returns:
#             str: Text đã được transcribe
#         """
#         # Lưu bytes vào temporary file
#         suffix = Path(filename).suffix
#         with tempfile.NamedTemporaryFile(delete=False, suffix=suffix) as temp_file:
#             temp_file.write(audio_bytes)
#             temp_file_path = temp_file.name
        
#         try:
#             # Transcribe từ temporary file
#             transcribed_text = await self.transcribe_audio(temp_file_path, language)
#             return transcribed_text
#         finally:
#             # Xóa temporary file
#             if os.path.exists(temp_file_path):
#                 os.unlink(temp_file_path)
