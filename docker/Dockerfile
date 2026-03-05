FROM eclipse-temurin:21-jdk

RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    python3 \
    python3-pip \
    ffmpeg \
    libchromaprint-tools \
    ca-certificates \
    && rm -rf /var/lib/apt/lists/*

RUN pip3 install --no-cache-dir yt-dlp

WORKDIR /workspace
COPY . /workspace

RUN chmod +x docker/build.sh

ENTRYPOINT ["./docker/build.sh"]
