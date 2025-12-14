<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Miles of Smiles - AI Assistant</title>
    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&display=swap" rel="stylesheet">
    <style>
        * {
            margin: 0;
            padding: 0;
            box-sizing: border-box;
        }

        body {
            font-family: 'Inter', -apple-system, BlinkMacSystemFont, sans-serif;
            background: linear-gradient(135deg, #1a1a2e 0%, #16213e 50%, #0f3460 100%);
            min-height: 100vh;
            display: flex;
            flex-direction: column;
            align-items: center;
            padding: 20px;
        }

        .header {
            display: flex;
            align-items: center;
            gap: 16px;
            margin-bottom: 20px;
            padding: 20px;
        }

        .logo {
            height: 60px;
            filter: drop-shadow(0 4px 8px rgba(0, 0, 0, 0.3));
        }

        .title-container {
            text-align: left;
        }

        .title {
            color: #fff;
            font-size: 28px;
            font-weight: 700;
            text-shadow: 0 2px 10px rgba(0, 0, 0, 0.3);
        }

        .subtitle {
            color: #94a3b8;
            font-size: 14px;
            font-weight: 400;
            margin-top: 4px;
        }

        .powered-by {
            display: flex;
            align-items: center;
            gap: 8px;
            color: #64748b;
            font-size: 12px;
            margin-top: 8px;
        }

        .powered-by img {
            height: 20px;
            opacity: 0.8;
        }

        .chat-container {
            width: 100%;
            max-width: 800px;
            background: rgba(255, 255, 255, 0.05);
            backdrop-filter: blur(10px);
            border-radius: 20px;
            border: 1px solid rgba(255, 255, 255, 0.1);
            box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.5);
            overflow: hidden;
            display: flex;
            flex-direction: column;
            height: calc(100vh - 200px);
            min-height: 500px;
        }

        .chat-messages {
            flex: 1;
            overflow-y: auto;
            padding: 24px;
            display: flex;
            flex-direction: column;
            gap: 16px;
        }

        .chat-messages::-webkit-scrollbar {
            width: 6px;
        }

        .chat-messages::-webkit-scrollbar-track {
            background: rgba(255, 255, 255, 0.05);
        }

        .chat-messages::-webkit-scrollbar-thumb {
            background: rgba(255, 255, 255, 0.2);
            border-radius: 3px;
        }

        .message {
            display: flex;
            gap: 12px;
            max-width: 85%;
            animation: fadeIn 0.3s ease-out;
        }

        @keyframes fadeIn {
            from {
                opacity: 0;
                transform: translateY(10px);
            }
            to {
                opacity: 1;
                transform: translateY(0);
            }
        }

        .message.user {
            align-self: flex-end;
            flex-direction: row-reverse;
        }

        .message-avatar {
            width: 36px;
            height: 36px;
            border-radius: 50%;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 12px;
            font-weight: 600;
            color: #fff;
            flex-shrink: 0;
        }

        .message.assistant .message-avatar {
            background: linear-gradient(135deg, #e94560 0%, #ff6b6b 100%);
        }

        .message.user .message-avatar {
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
        }

        .message-content {
            padding: 14px 18px;
            border-radius: 18px;
            line-height: 1.5;
            font-size: 14px;
        }

        .message.assistant .message-content {
            background: rgba(255, 255, 255, 0.1);
            color: #e2e8f0;
            border-bottom-left-radius: 4px;
        }

        .message.user .message-content {
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: #fff;
            border-bottom-right-radius: 4px;
        }

        .chat-input-container {
            padding: 20px 24px;
            background: rgba(0, 0, 0, 0.2);
            border-top: 1px solid rgba(255, 255, 255, 0.1);
        }

        .chat-input-wrapper {
            display: flex;
            gap: 12px;
            align-items: center;
        }

        .chat-input {
            flex: 1;
            padding: 14px 20px;
            background: rgba(255, 255, 255, 0.1);
            border: 1px solid rgba(255, 255, 255, 0.1);
            border-radius: 25px;
            color: #fff;
            font-size: 14px;
            font-family: inherit;
            outline: none;
            transition: all 0.3s ease;
        }

        .chat-input::placeholder {
            color: #64748b;
        }

        .chat-input:focus {
            border-color: #667eea;
            box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.2);
        }

        .send-button {
            width: 48px;
            height: 48px;
            border-radius: 50%;
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            border: none;
            cursor: pointer;
            display: flex;
            align-items: center;
            justify-content: center;
            transition: all 0.3s ease;
            flex-shrink: 0;
        }

        .send-button:hover {
            transform: scale(1.05);
            box-shadow: 0 4px 15px rgba(102, 126, 234, 0.4);
        }

        .send-button:disabled {
            opacity: 0.5;
            cursor: not-allowed;
            transform: none;
        }

        .send-button svg {
            width: 20px;
            height: 20px;
            fill: white;
        }

        .typing-indicator {
            display: flex;
            gap: 4px;
            padding: 14px 18px;
            background: rgba(255, 255, 255, 0.1);
            border-radius: 18px;
            border-bottom-left-radius: 4px;
            width: fit-content;
        }

        .typing-indicator span {
            width: 8px;
            height: 8px;
            background: #94a3b8;
            border-radius: 50%;
            animation: bounce 1.4s infinite ease-in-out;
        }

        .typing-indicator span:nth-child(1) {
            animation-delay: -0.32s;
        }

        .typing-indicator span:nth-child(2) {
            animation-delay: -0.16s;
        }

        @keyframes bounce {
            0%, 80%, 100% {
                transform: scale(0);
            }
            40% {
                transform: scale(1);
            }
        }

        .welcome-message {
            text-align: center;
            color: #94a3b8;
            padding: 40px 20px;
        }

        .welcome-message h2 {
            color: #e2e8f0;
            font-size: 20px;
            margin-bottom: 12px;
        }

        .welcome-message p {
            font-size: 14px;
            line-height: 1.6;
            max-width: 400px;
            margin: 0 auto;
        }

        .suggestions {
            display: flex;
            flex-wrap: wrap;
            gap: 8px;
            justify-content: center;
            margin-top: 20px;
        }

        .suggestion {
            padding: 8px 16px;
            background: rgba(255, 255, 255, 0.1);
            border: 1px solid rgba(255, 255, 255, 0.1);
            border-radius: 20px;
            color: #94a3b8;
            font-size: 13px;
            cursor: pointer;
            transition: all 0.3s ease;
        }

        .suggestion:hover {
            background: rgba(255, 255, 255, 0.15);
            color: #e2e8f0;
            border-color: rgba(255, 255, 255, 0.2);
        }

        .error-message {
            background: rgba(239, 68, 68, 0.2);
            border: 1px solid rgba(239, 68, 68, 0.3);
            color: #fca5a5;
        }
    </style>
</head>
<body>
    <div class="header">
        <img src="langchain4j-cdi-logo.png"
             alt="langchain4j-cdi Logo"
             class="logo">
        <div class="title-container">
            <h1 class="title">Miles of Smiles</h1>
            <p class="subtitle">AI-Powered Car Rental Assistant</p>
            <div class="powered-by">
                Powered by langchain4j-cdi on Payara Micro
            </div>
        </div>
    </div>

    <div class="chat-container">
        <div class="chat-messages" id="chatMessages">
            <div class="welcome-message">
                <h2>Welcome to Miles of Smiles!</h2>
                <p>I'm your AI assistant. I can help you with car bookings, cancellations, and answer questions about our services.</p>
                <div class="suggestions">
                    <span class="suggestion" onclick="sendSuggestion('What are your cancellation policies?')">Cancellation policies</span>
                    <span class="suggestion" onclick="sendSuggestion('I want to book a car')">Book a car</span>
                    <span class="suggestion" onclick="sendSuggestion('Tell me about Miles of Smiles')">About us</span>
                </div>
            </div>
        </div>

        <div class="chat-input-container">
            <div class="chat-input-wrapper">
                <input type="text"
                       class="chat-input"
                       id="chatInput"
                       placeholder="Type your message..."
                       onkeypress="handleKeyPress(event)">
                <button class="send-button" id="sendButton" onclick="sendMessage()">
                    <svg viewBox="0 0 24 24">
                        <path d="M2.01 21L23 12 2.01 3 2 10l15 2-15 2z"/>
                    </svg>
                </button>
            </div>
        </div>
    </div>

    <script>
        const chatMessages = document.getElementById('chatMessages');
        const chatInput = document.getElementById('chatInput');
        const sendButton = document.getElementById('sendButton');
        let isFirstMessage = true;

        function handleKeyPress(event) {
            if (event.key === 'Enter' && !event.shiftKey) {
                event.preventDefault();
                sendMessage();
            }
        }

        function sendSuggestion(text) {
            chatInput.value = text;
            sendMessage();
        }

        function addMessage(content, type) {
            if (isFirstMessage) {
                const welcome = chatMessages.querySelector('.welcome-message');
                if (welcome) welcome.remove();
                isFirstMessage = false;
            }

            const messageDiv = document.createElement('div');
            messageDiv.className = 'message ' + type;

            const avatar = document.createElement('div');
            avatar.className = 'message-avatar';
            avatar.textContent = type === 'user' ? 'U' : 'AI';

            const contentDiv = document.createElement('div');
            contentDiv.className = 'message-content';
            contentDiv.textContent = content;

            messageDiv.appendChild(avatar);
            messageDiv.appendChild(contentDiv);
            chatMessages.appendChild(messageDiv);

            chatMessages.scrollTop = chatMessages.scrollHeight;
        }

        function showTypingIndicator() {
            const typingDiv = document.createElement('div');
            typingDiv.className = 'message assistant';
            typingDiv.id = 'typingIndicator';

            const avatar = document.createElement('div');
            avatar.className = 'message-avatar';
            avatar.textContent = 'AI';

            const indicator = document.createElement('div');
            indicator.className = 'typing-indicator';
            indicator.innerHTML = '<span></span><span></span><span></span>';

            typingDiv.appendChild(avatar);
            typingDiv.appendChild(indicator);
            chatMessages.appendChild(typingDiv);

            chatMessages.scrollTop = chatMessages.scrollHeight;
        }

        function hideTypingIndicator() {
            const typing = document.getElementById('typingIndicator');
            if (typing) typing.remove();
        }

        async function sendMessage() {
            const message = chatInput.value.trim();
            if (!message) return;

            chatInput.value = '';
            sendButton.disabled = true;
            chatInput.disabled = true;

            addMessage(message, 'user');
            showTypingIndicator();

            try {
                const response = await fetch('/api/car-booking/chat?question=' + encodeURIComponent(message));
                const text = await response.text();

                hideTypingIndicator();
                addMessage(text, 'assistant');
            } catch (error) {
                hideTypingIndicator();
                addMessage('Sorry, there was an error processing your request. Please try again.', 'assistant error-message');
                console.error('Error:', error);
            } finally {
                sendButton.disabled = false;
                chatInput.disabled = false;
                chatInput.focus();
            }
        }

        // Focus input on load
        chatInput.focus();
    </script>
</body>
</html>