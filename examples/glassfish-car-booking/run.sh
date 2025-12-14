#!/bin/bash
# Source the common Ollama setup script
SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
source "$SCRIPT_DIR/../ollama-setup.sh"

# Function to cleanup on exit
cleanup() {
    echo "Cleaning up..."
    cleanup_ollama
    exit 0
}

# Set up trap to catch SIGINT (Ctrl+C) and SIGTERM
trap cleanup SIGINT SIGTERM

# Setup Ollama
setup_ollama

WORKING_DIR="$(dirname "${BASH_SOURCE[0]}")"
GLASSFISH_DIR=$WORKING_DIR/target/cargo/installs/glassfish-7.0.16/glassfish7/
echo $WORKING_DIR

if [[ ! -d $GLASSFISH_DIR ]];then
    echo "Installing Glassfish"
    mvn cargo:install
fi

mvn install cargo:run
