FROM ubuntu:latest
LABEL authors="mimche"

ENTRYPOINT ["top", "-b"]