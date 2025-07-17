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
    // Generate HTML replay using kotlinx.html
    val htmlContent = createHTML().html {
        head {
            meta(charset = "utf-8")
            title { +"Quordle Replay" }
            style {
                unsafe {
                    raw("""
                body { margin: 0; padding: 0; font-family: sans-serif; }
                .container { display: flex; height: 100vh; }
                    .left { flex: 1; padding: 10px; background: #f0f0f0; }
                    .right { flex: 1; padding: 10px; background: #ffffff; position: relative; display: flex; flex-direction: column; overflow: auto; }
                    .boards-grid { display: grid; grid-template-columns: repeat(2, 1fr); grid-template-rows: repeat(2, 1fr); gap: 20px; height: calc(100% - 20px); }
                    .board-container { background: #ffffff; padding: 10px; border-radius: 4px; display: flex; flex-direction: column; height: 100%; }
                    .board { display: grid; grid-template-columns: repeat(5, 1fr); gap: 4px; width: 100%; height: 100%; }
                    .board-row { flex: 1; display: flex; }
                    .tile { flex: 1; aspect-ratio: 1; min-width: 0; display: flex; justify-content: center; align-items: center; font-weight: bold; color: #000; }
                    .tile.CORRECT { background: #6aaa64; }
                    .tile.PRESENT { background: #c9b458; }
                    .tile.ABSENT { background: #787c7e; }
                    .tile.EMPTY { background: #ffffff; border: 1px solid #ccc; }
                    .tile.placeholder { background: #ddd; }
                    .message { margin: 5px; padding: 8px 12px; border-radius: 8px; white-space: pre-wrap; max-width: 70%; }
                    .message[data-role="user"] { align-self: flex-end; background-color: #e1ffc7; }
                    .message[data-role="assistant"] { align-self: flex-start; background-color: #ffffff; border: 1px solid #ddd; }
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
                    // change board rows to static 9 rows with placeholders
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
                                        div(classes = "board") {
                                            // initial placeholders
                                            repeat(5) { span(classes = "tile placeholder EMPTY") {} }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                div(classes = "right") {
                    allMessages.forEachIndexed { idx, msg ->
                        div(classes = "message hidden") {
                            attributes["data-index"] = idx.toString()
                            attributes["data-role"] = msg.role.role
                            when (msg.role) {
                                ChatRole.System -> {} // skip
                                ChatRole.User -> {
                                    small(classes = "role-indicator") { i { +"user" } }
                                    p { +msg.content.orEmpty() }
                                }
                                ChatRole.Assistant -> {
                                    small(classes = "role-indicator") { i { +"LLM" } }
                                    p { +msg.content.orEmpty() }
                                }
                                else -> {
                                    p { +msg.content.orEmpty() }
                                }
                            }
                        }
                    }
                }
            }
            script {
                unsafe {
                    raw("""
                    const messages = document.querySelectorAll('.message');
                    let current = 0;
                    let attemptCount = 0;
                    function revealRowsForAttempt(idx) {
                        document.querySelectorAll('[data-attempt-index="'+idx+'"]').forEach(row => {
                            const word = row.getAttribute('data-word') || '';
                            const feedback = row.getAttribute('data-feedback')?.split(',') || [];
                            const tiles = row.querySelectorAll('.tile');
                            tiles.forEach((tile, i) => {
                                // update tile classes and text
                                tile.classList.remove('placeholder', 'EMPTY');
                                if (feedback[i]) {
                                    tile.classList.add(feedback[i]);
                                }
                                tile.innerText = word.charAt(i) || '';
                            });
                        });
                    }
                    function showNext() {
                        if (current >= messages.length) return;
                        const el = messages[current];
                        const role = el.getAttribute('data-role');
                        el.classList.remove('hidden');
                        // Auto-scroll chat to newest message
                        const container = document.querySelector('.right');
                        setTimeout(() => { container.scrollTop = container.scrollHeight; }, 100);
                        const text = el.innerText;
                        el.innerText = '';
                        let i = 0;
                        function typeChar() {
                            if (i < text.length) {
                                el.innerText += text.charAt(i);
                                i++;
                                setTimeout(typeChar, 0);
                            } else {
                                if (role === 'assistant') {
                                    revealRowsForAttempt(attemptCount);
                                    attemptCount++;
                                }
                                current++;
                                setTimeout(showNext, 500);
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
