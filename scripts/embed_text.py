#!/usr/bin/env python3
"""Simple CLI to generate sentence embeddings with caching."""
import argparse
from sentence_transformers import SentenceTransformer

from tools.embed_cache import load_embeddings, save_embeddings


MODEL_NAME = "all-MiniLM-L6-v2"


def main() -> None:
    parser = argparse.ArgumentParser(description="Generate embeddings for text")
    parser.add_argument("text", help="text to embed")
    args = parser.parse_args()

    key = args.text
    emb = load_embeddings(key)
    if emb is None:
        model = SentenceTransformer(MODEL_NAME)
        emb = model.encode([args.text])
        save_embeddings(key, emb)

    print(emb)


if __name__ == "__main__":
    main()
