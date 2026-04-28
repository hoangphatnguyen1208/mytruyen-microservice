# import os
# import tempfile
# import re
# from typing import Optional
# import yt_dlp


# class YouTubeAudioDownloader:
#     """
#     Service để tải audio từ YouTube URL
#     """
    
#     @staticmethod
#     def is_valid_youtube_url(url: str) -> bool:
#         """
#         Kiểm tra xem URL có phải là YouTube URL hợp lệ không
        
#         Args:
#             url: URL cần kiểm tra
            
#         Returns:
#             bool: True nếu là YouTube URL hợp lệ
#         """
#         youtube_regex = (
#             r'(https?://)?(www\.)?'
#             r'(youtube|youtu|youtube-nocookie)\.(com|be)/'
#             r'(watch\?v=|embed/|v/|.+\?v=)?([^&=%\?]{11})'
#         )
#         youtube_regex_match = re.match(youtube_regex, url)
#         return youtube_regex_match is not None
    
#     @staticmethod
#     def extract_video_id(url: str) -> Optional[str]:
#         """
#         Trích xuất video ID từ YouTube URL
        
#         Args:
#             url: YouTube URL
            
#         Returns:
#             str: Video ID hoặc None nếu không tìm thấy
#         """
#         youtube_regex = (
#             r'(https?://)?(www\.)?'
#             r'(youtube|youtu|youtube-nocookie)\.(com|be)/'
#             r'(watch\?v=|embed/|v/|.+\?v=)?([^&=%\?]{11})'
#         )
#         match = re.match(youtube_regex, url)
#         if match:
#             return match.group(6)
#         return None
    
#     async def download_audio(self, url: str, language: str = "vi") -> tuple[bytes, str]:
#         """
#         Tải audio từ YouTube URL
        
#         Args:
#             url: YouTube URL
#             language: Ngôn ngữ (để đặt tên file tạm)
            
#         Returns:
#             tuple: (audio_bytes, filename)
            
#         Raises:
#             Exception: Nếu có lỗi khi tải audio
#         """
#         if not self.is_valid_youtube_url(url):
#             raise ValueError("URL không phải là YouTube URL hợp lệ")
        
#         # Tạo thư mục tạm thời để lưu file
#         temp_dir = tempfile.mkdtemp()
        
#         try:
#             # Cấu hình yt-dlp - tải audio trực tiếp mà không cần ffmpeg
#             ydl_opts = {
#                 'format': 'bestaudio/best',
#                 # Không dùng postprocessors để tránh cần ffmpeg
#                 'outtmpl': os.path.join(temp_dir, 'audio.%(ext)s'),
#                 'quiet': True,
#                 'no_warnings': True,
#                 'extract_flat': False,
#             }
            
#             # Tải audio
#             with yt_dlp.YoutubeDL(ydl_opts) as ydl:
#                 info = ydl.extract_info(url, download=True)
#                 video_title = info.get('title', 'youtube_audio')
#                 downloaded_file = ydl.prepare_filename(info)
            
#             # Kiểm tra file đã tải
#             if not os.path.exists(downloaded_file):
#                 raise Exception("Không thể tải audio từ YouTube")
            
#             # Đọc file
#             with open(downloaded_file, 'rb') as f:
#                 audio_bytes = f.read()
            
#             # Tạo tên file từ video title với extension thực tế
#             file_extension = os.path.splitext(downloaded_file)[1]
#             safe_title = re.sub(r'[^\w\s-]', '', video_title)
#             safe_title = re.sub(r'[-\s]+', '-', safe_title)
#             filename = f"{safe_title[:50]}{file_extension}"
            
#             return audio_bytes, filename
            
#         except Exception as e:
#             raise Exception(f"Lỗi khi tải audio từ YouTube: {str(e)}")
            
#         finally:
#             # Dọn dẹp thư mục tạm
#             try:
#                 import shutil
#                 shutil.rmtree(temp_dir)
#             except Exception:
#                 pass
    
#     async def get_video_info(self, url: str) -> dict:
#         """
#         Lấy thông tin video từ YouTube URL
        
#         Args:
#             url: YouTube URL
            
#         Returns:
#             dict: Thông tin video (title, duration, uploader, etc.)
#         """
#         if not self.is_valid_youtube_url(url):
#             raise ValueError("URL không phải là YouTube URL hợp lệ")
        
#         try:
#             ydl_opts = {
#                 'quiet': True,
#                 'no_warnings': True,
#                 'extract_flat': False,
#             }
            
#             with yt_dlp.YoutubeDL(ydl_opts) as ydl:
#                 info = ydl.extract_info(url, download=False)
                
#                 return {
#                     'title': info.get('title'),
#                     'duration': info.get('duration'),
#                     'uploader': info.get('uploader'),
#                     'description': info.get('description'),
#                     'thumbnail': info.get('thumbnail'),
#                     'view_count': info.get('view_count'),
#                 }
                
#         except Exception as e:
#             raise Exception(f"Lỗi khi lấy thông tin video: {str(e)}")
