from fastapi import FastAPI
from app.api.main import api_router
from app.core.config import settings
from starlette.exceptions import HTTPException as StarletteHTTPException
from app.schema.response import Response
from fastapi.responses import JSONResponse
from starlette.middleware.cors import CORSMiddleware
# import torch
# from FlagEmbedding import BGEM3FlagModel
# from pinecone import Pinecone
# from faster_whisper import WhisperModel
# from contextlib import asynccontextmanager
# import logging

# device = torch.device("cpu")
# logger = logging.getLogger("uvicorn")

# @asynccontextmanager
# async def lifespan(app: FastAPI):
#     logger.info("⏳ Loading models & Pinecone...")
#     app.state.model = BGEM3FlagModel('BAAI/bge-m3', device='cpu')
#     app.state.whisper_model = WhisperModel('turbo', device='cpu', compute_type='int8')
#     app.state.pc = Pinecone(api_key=settings.PINECONE_API_KEY)
#     app.state.pc_index = app.state.pc.Index("hybrid-spilt")
#     logger.info("✅ Models & Pinecone loaded successfully.")
#     yield

#     del app.state.model
#     del app.state.whisper_model
#     del app.state.pc
#     del app.state.pc_index

app = FastAPI(
    title=settings.PROJECT_NAME,
    # lifespan=lifespan
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

@app.exception_handler(StarletteHTTPException)
async def http_exception_handler(request, exc):
    return JSONResponse(
        status_code=exc.status_code,
        content=Response(
            status_code=exc.status_code,
            success=False,
            message=str(exc.detail),
            data=None
        ).model_dump()
    )

app.include_router(api_router, prefix=settings.API_V1_STR)
