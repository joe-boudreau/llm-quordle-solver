import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import kotlinx.html.i
import kotlinx.html.small
import kotlinx.html.body
import kotlinx.html.div
import kotlinx.html.head
import kotlinx.html.html
import kotlinx.html.meta
import kotlinx.html.p
import kotlinx.html.script
import kotlinx.html.span
import kotlinx.html.stream.createHTML
import kotlinx.html.style
import kotlinx.html.title
import kotlinx.html.unsafe
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.collections.set

fun main() {
    val (gameState, allMessages) = loadGameState()
    regenerateHtmlReplay(gameState, allMessages)
}

fun regenerateHtmlReplay(
    gameState: GameState,
    allMessages: MutableList<ChatMessage>
) {
    // Clear existing replay file
    File("replay.html").writeText("")

    // Save the HTML replay
    saveHtmlReplay(gameState, allMessages)
}

fun saveHtmlReplay(
    gameState: GameState,
    allMessages: MutableList<ChatMessage>
) {
    val systemMessage = allMessages.firstOrNull { it.role == ChatRole.System }

    val llmGuessResponses = allMessages
        .filter { it.role == ChatRole.Assistant }
        .map {Json.decodeFromString<QuordleGuessResponse>(it.content?.trim() ?: "") }

            // Generate HTML replay using kotlinx.html
    val htmlContent = createHTML().html {
        head {
            meta(charset = "utf-8")
            title { +"Quordle Replay" }
            style {
                unsafe {
                    raw("""
                body { margin: 0; padding: 0; font-family: 'Clear Sans', 'Helvetica Neue', Arial, sans-serif; }
                .container { display: flex; height: 100vh; }
                    .left { flex: 1; padding: 20px; background: #f0f0f0; overflow: auto; }
                    .right { flex: 1; padding: 10px; background: #ffffff; position: relative; display: flex; flex-direction: column; overflow: auto; }
                    .boards-grid { display: grid; grid-template-columns: repeat(2, 1fr); grid-template-rows: repeat(2, 1fr); gap: 12px; width: 320px; margin-bottom: 20px; }
                    .board-container { background: #ffffff; border: 1px solid #d3d6da; border-radius: 4px; padding: 8px; }
                    .board-row { display: flex; gap: 4px; margin-bottom: 4px; }
                    .board-row:last-child { margin-bottom: 0; }
                    .tile { width: 28px; height: 28px; display: flex; justify-content: center; align-items: center; font-weight: bold; font-size: 14px; text-transform: uppercase; border: 2px solid #d3d6da; }
                    .tile.CORRECT { background-color: #6aaa64; border-color: #6aaa64; color: white; }
                    .tile.PRESENT { background-color: #c9b458; border-color: #c9b458; color: white; }
                    .tile.ABSENT { background-color: #787c7e; border-color: #787c7e; color: white; }
                    .tile.EMPTY { background-color: #ffffff; border-color: #d3d6da; color: #000000; }
                    .tile.placeholder { background-color: #ffffff; border-color: #d3d6da; color: transparent; }
                    .message { margin: 5px; padding: 12px 16px; border-radius: 8px; max-width: 80%; word-wrap: break-word; }
                    .message p { margin: 0; padding: 0; white-space: pre-wrap; line-height: 1.4; }
                    .message.system { background-color: #e3f2fd; border: 1px solid #90caf9; align-self: flex-start; font-style: italic; }
                    .message.reasoning { align-self: flex-start; background-color: #f5f5f5; border: 1px solid #ddd; }
                    .message.guess { align-self: flex-start; background-color: #e8f5e8; border: 1px solid #6aaa64; }
                    .guess-word { font-weight: bold; text-transform: uppercase; }
                    .hidden { display: none; }
                    .role-indicator { display: block; font-style: italic; font-size: 0.75em; margin-bottom: 4px; color: #666; }
                """
                    )
                }
            }
        }
        body {
            div(classes = "container") {
                div(classes = "left") {
                    // Game boards
                    div(classes = "boards-grid") {
                        gameState.boardStates.forEach { boardState ->
                            div(classes = "board-container") {
                                val attempts = boardState.attempts
                                for (ai in 0 until 9) {
                                    div(classes = "board-row") {
                                        attributes["data-attempt-index"] = ai.toString()
                                        if (ai < attempts.size) {
                                            // store data for reveal
                                            attributes["data-word"] = attempts[ai].word
                                            attributes["data-feedback"] = attempts[ai].feedback.joinToString(",") { it.name }
                                        }
                                        // tiles should be direct children of board-row
                                        repeat(5) { span(classes = "tile placeholder EMPTY") {} }
                                    }
                                }
                            }
                        }
                    }
                }
                div(classes = "right") {
                    // Show system message first
                    systemMessage?.let { msg ->
                        div(classes = "message system") {
                            attributes["data-index"] = "system"
                            attributes["data-role"] = "system"
                            small(classes = "role-indicator") { i { +"System" } }
                            p { +msg.content.orEmpty() }
                        }
                    }

                    // Show reasoning and guess messages in pairs
                    llmGuessResponses.forEachIndexed { index, response ->
                        // Reasoning message
                        div(classes = "message reasoning hidden") {
                            attributes["data-index"] = "reasoning-$index"
                            attributes["data-role"] = "reasoning"
                            attributes["data-attempt-index"] = index.toString()
                            small(classes = "role-indicator") { i { +"LLM" } }
                            p { +response.reasoning }
                        }

                        // Guess message
                        div(classes = "message guess hidden") {
                            attributes["data-index"] = "guess-$index"
                            attributes["data-role"] = "guess"
                            attributes["data-attempt-index"] = index.toString()
                            small(classes = "role-indicator") { i { +"LLM" } }
                            p {
                                +"Guess: "
                                span(classes = "guess-word") { +response.finalAnswer }
                            }
                        }
                    }
                }
            }
            script {
                unsafe {
                    raw("""
                    const allMessages = document.querySelectorAll('.message:not(.system)');
                    let current = 0;
                    let attemptCount = 0;
                    
                    function revealRowsForAttempt(idx) {
                        document.querySelectorAll('[data-attempt-index="'+idx+'"]').forEach(row => {
                            const word = row.getAttribute('data-word') || '';
                            const feedback = row.getAttribute('data-feedback')?.split(',') || [];
                            const tiles = row.querySelectorAll('.tile');
                            tiles.forEach((tile, i) => {
                                tile.classList.remove('placeholder', 'EMPTY');
                                if (feedback[i]) {
                                    tile.classList.add(feedback[i]);
                                }
                                tile.innerText = word.charAt(i) || '';
                            });
                        });
                    }
                    
                    function showGuessMessage(attemptIndex) {
                        const guessMessage = document.querySelector('[data-index="guess-'+attemptIndex+'"]');
                        if (guessMessage) {
                            guessMessage.classList.remove('hidden');
                            
                            // Auto-scroll to show the guess message
                            const container = document.querySelector('.right');
                            guessMessage.scrollIntoView({ behavior: 'smooth', block: 'end' });
                        }
                    }
                    
                    function autoScroll() {
                        const container = document.querySelector('.right');
                        container.scrollTop = container.scrollHeight;
                    }
                    
                    function showNext() {
                        if (current >= allMessages.length) return;
                        const el = allMessages[current];
                        const role = el.getAttribute('data-role');
                        el.classList.remove('hidden');
                        
                        // Scroll to show the new message immediately
                        setTimeout(() => {
                            el.scrollIntoView({ behavior: 'smooth', block: 'end' });
                        }, 100);
                        
                        if (role === 'guess') {
                            // Guess messages don't need typing animation, just show them
                            current++;
                            setTimeout(showNext, 800);
                            return;
                        }
                        
                        // For reasoning messages, get the content from the p tag
                        const contentP = el.querySelector('p');
                        const text = contentP.innerText;
                        contentP.innerText = '';
                        let i = 0;
                        let lastScrollTime = Date.now();
                        
                        function typeChar() {
                            if (i < text.length) {
                                contentP.innerText += text.charAt(i);
                                i++;
                                
                                // Scroll periodically during typing to keep content visible
                                const now = Date.now();
                                if (now - lastScrollTime > 200) {
                                    autoScroll();
                                    lastScrollTime = now;
                                }
                                
                                setTimeout(typeChar, 0);
                            } else {
                                if (role === 'reasoning') {
                                    // Get the attempt index directly from the message attribute
                                    const attemptIndex = parseInt(el.getAttribute('data-attempt-index'));
                                    // Show board tiles and guess message for this attempt
                                    showGuessMessage(attemptIndex);
                                    setTimeout(() => revealRowsForAttempt(attemptIndex), 500);
                                }
                                current++;
                                setTimeout(showNext, 1000);
                            }
                        }
                        typeChar();
                    }
                    
                    window.onload = showNext;
                 """
                    )
                }
            }
        }
    }

    // Write to static path
    File("replay.html").writeText(htmlContent)
    println("Replay saved to replay.html")
}
