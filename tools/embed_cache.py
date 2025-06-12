import os
import hashlib
from pathlib import Path
from typing import Optional

import numpy as np


def _cache_dir() -> Path:
    base = os.environ.get(
        "EMBED_CACHE_DIR",
        os.path.expanduser("~/.cache/grpc-service-benchmark/embeddings"),
    )
    path = Path(base)
    path.mkdir(parents=True, exist_ok=True)
    return path


def _file_path(key: str) -> Path:
    hashed = hashlib.sha256(key.encode("utf-8")).hexdigest()
    return _cache_dir() / f"{hashed}.npy"


def load_embeddings(key: str) -> Optional[np.ndarray]:
    path = _file_path(key)
    if path.exists():
        return np.load(path)
    return None


def save_embeddings(key: str, data: np.ndarray) -> None:
    path = _file_path(key)
    np.save(path, data)
