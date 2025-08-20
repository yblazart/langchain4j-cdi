#!/bin/bash
# Made using Junie
# Common script for Ollama setup (local/docker/podman)

# Function to check if a command exists
command_exists() {
    command -v "$1" >/dev/null 2>&1
}

# Function to setup Ollama and return variables
setup_ollama() {
    local OLLAMA_PID=""
    local CONTAINER_PID=""
    local CONTAINER_ENGINE=""

    # Check if Ollama is installed locally
    if command_exists ollama; then
        echo "Ollama is installed locally."
        
        # Check if Ollama is already running
        if pgrep -x "ollama" >/dev/null; then
            echo "Ollama is already running."
        else
            echo "Starting Ollama locally..."
            ollama serve &
            OLLAMA_PID=$!
            echo "Ollama started with PID: $OLLAMA_PID"
            # Give it a moment to start up
            sleep 2
        fi
        
        # Pull llama3.1 model if not already present
        if ! ollama list | grep -q "llama3.1"; then
            echo "Pulling llama3.1 model..."
            ollama pull llama3.1:latest
        fi
    else
        echo "Ollama is not installed locally. Checking for container engines..."
        
        # Check for podman or docker
        if command_exists podman; then
            CONTAINER_ENGINE="podman"
            echo "Using podman as container engine."
        elif command_exists docker; then
            CONTAINER_ENGINE="docker"
            echo "Using docker as container engine."
        else
            echo "Error: Neither Ollama, podman, nor docker is available. Please install one of them."
            return 1
        fi
        
        # Stop any existing Ollama container
        echo "Stopping any existing Ollama container..."
        $CONTAINER_ENGINE stop ollama >/dev/null 2>&1
        
        # Start Ollama container
        echo "Starting Ollama container..."
        $CONTAINER_ENGINE run -d --name ollama --replace --pull=always --restart=always -p 11434:11434 -v ollama:/root/.ollama --stop-signal=SIGKILL docker.io/ollama/ollama
        CONTAINER_PID=1  # Just a flag to indicate we're using a container
        
        # Give it a moment to start up
        sleep 2
        
        # Pull llama3.1 model if needed
        echo "Ensuring llama3.1 model is available..."
        $CONTAINER_ENGINE exec ollama ollama pull llama3.1:latest
    fi

    # Export variables to be used by the calling script
    export OLLAMA_PID="$OLLAMA_PID"
    export CONTAINER_PID="$CONTAINER_PID"
    export CONTAINER_ENGINE="$CONTAINER_ENGINE"
}

# Function to cleanup Ollama
cleanup_ollama() {
    echo "Cleaning up Ollama..."
    if [ -n "$CONTAINER_PID" ]; then
        echo "Stopping Ollama container..."
        $CONTAINER_ENGINE stop ollama >/dev/null 2>&1
    elif [ -n "$OLLAMA_PID" ]; then
        echo "Stopping local Ollama service..."
        kill $OLLAMA_PID >/dev/null 2>&1
    fi
}