import com.aallam.openai.api.chat.ChatMessage
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
    val gameReplayData = loadGameState()

    // Clear existing replay file
    File(REPLAY_HTML_FILENAME).writeText("")

    // Save the HTML replay
    saveHtmlReplay(
        gameState = gameReplayData.gameState,
        wordAnswers = gameReplayData.wordAnswers,
        systemMessage = gameReplayData.systemMessage,
        guessChat = gameReplayData.guessChat,
        finalMessages = gameReplayData.finalMessages,
    )
}

const val HTML_FILENAME = "daily-quordle-solver.html"
private val REPLAY_HTML_FILENAME = OUTPUT_FILEPATH + HTML_FILENAME

fun saveHtmlReplay(
    gameState: GameState,
    wordAnswers: List<String>,
    systemMessage: ChatMessage,
    guessChat: List<Pair<ChatMessage, QuordleGuessResponse>>,
    finalMessages: List<ChatMessage>,
    s3Repository: S3BucketRepository? = null
) {

    val todaysDate = java.time.LocalDate.now().toString()
    val modelId = System.getenv("OPENAI_CHAT_MODEL_ID") ?: "N/A"
    val reasoningEffort = System.getenv("OPENAI_REASONING_EFFORT") ?: "N/A"

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
                    .right { flex: 1; padding: 10px; background: #ffffff; position: relative; display: flex; flex-direction: column; overflow: auto; margin: 10 10 30 10px;}
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
                    .message.system { background-color: #e3f2fd; border: 1px solid #90caf9; align-self: flex-start; }
                    .message.user { align-self: flex-end; background-color: #fce4ec; border: 1px solid #f48fb1; }
                    .message.user.collapsed { cursor: pointer; min-height: 20px; padding: 8px 16px; }
                    .message.user.collapsed .message-content { display: none; }
                    .message.user.collapsed .role-indicator::after { content: " (click to expand)"; color: #999; }
                    .message.user.expanded .message-content { display: block; }
                    .message.assistant { align-self: flex-start; background-color: #e8f5e8; border: 1px solid #6aaa64; }
                    .message.reasoning { align-self: flex-start; background-color: #e8f5e8; border: 1px solid #6aaa64; }
                    .message.guess { align-self: flex-start; background-color: #e8f5e8; border: 1px solid #6aaa64; }
                    .guess-word { font-weight: bold; text-transform: uppercase; }
                    .hidden { display: none; }
                    .role-indicator { display: block; font-style: italic; font-size: 0.75em; margin-bottom: 4px; color: #666; }
                    .results-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 10px; width: 320px; margin-top: 20px; }
                    .results-grid.hidden { display: none; }
                    .result-word { padding: 15px; border-radius: 8px; text-align: center; font-weight: bold; font-size: 18px; text-transform: uppercase; color: white; border: 2px solid; }
                    .result-word.solved { background-color: #6aaa64; border-color: #6aaa64; }
                    .result-word.unsolved { background-color: #d73a49; border-color: #d73a49; }
                    .link { color: black; }
                    
                    /* Statistics Styles */
                    .stats-container { margin: 16px 0; padding: 16px; border: 1px solid #d3d6da; border-radius: 8px; background-color: #fafafa; font-style: normal; }
                    .stats-title { margin: 0 0 16px 0; font-size: 18px; font-weight: bold; color: #333; text-align: center; }
                    .stats-overview { display: flex; justify-content: space-around; margin-bottom: 24px; gap: 8px; }
                    .stat-item { text-align: center; flex: 1; }
                    .stat-number { font-size: 24px; font-weight: bold; color: #6aaa64; margin-bottom: 4px; }
                    .stat-label { font-size: 12px; color: #666; text-transform: uppercase; font-weight: 500; }
                    .stats-distribution { margin-top: 16px; }
                    .distribution-title { margin: 0 0 12px 0; font-size: 14px; font-weight: bold; color: #333; text-align: center; }
                    .stat-bars { display: flex; flex-direction: column; gap: 4px; }
                    .stat-bar-row { display: flex; align-items: center; gap: 8px; }
                    .stat-bar-label { width: 20px; text-align: center; font-size: 14px; font-weight: bold; color: #333; }
                    .stat-bar-container { flex: 1; height: 20px; background-color: #e0e0e0; border-radius: 4px; overflow: hidden; }
                    .stat-bar-fill { height: 100%; background-color: #6aaa64; transition: width 0.3s ease; min-width: 0; }
                    .stat-bar-count { width: 30px; text-align: center; font-size: 12px; color: #666; }
                    
                    /* Info footer styles */
                    .info-footer { position: fixed; bottom: 0; left: 0; right: 50%; background-color: rgba(240, 240, 240, 0.95); padding: 8px 20px; font-size: 12px; color: #666; border-top: 1px solid #d3d6da; display: flex; gap: 20px; z-index: 1000; }
                    .info-item { display: flex; gap: 4px; }
                    .info-label { font-weight: bold; }
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

                    // Results grid (hidden initially, shown after last guess)
                    div(classes = "results-grid hidden") {
                        attributes["id"] = "results-grid"
                        wordAnswers.forEachIndexed { index, word ->
                            val isSolved = gameState.boardStates[index].isSolved()
                            div(classes = "result-word ${if (isSolved) "solved" else "unsolved"}") {
                                +word
                            }
                        }
                    }

                    // Info footer with date, model, and reasoning effort
                    div(classes = "info-footer") {
                        div(classes = "info-item") {
                            span(classes = "info-label") { +"Date:" }
                            span { +todaysDate }
                        }
                        div(classes = "info-item") {
                            span(classes = "info-label") { +"Model:" }
                            span { +modelId }
                        }
                        div(classes = "info-item") {
                            span(classes = "info-label") { +"Reasoning:" }
                            span { +reasoningEffort }
                        }
                    }
                }
                div(classes = "right") {
                    // Show system message first
                    systemMessage.let { msg ->
                        div(classes = "message system") {
                            attributes["data-index"] = "system"
                            attributes["data-role"] = "system"
                            small(classes = "role-indicator") { i { +"System" } }
                            p { +msg.content.orEmpty() }
                        }
                    }

                    // Show reasoning and guess messages next
                    guessChat.forEachIndexed { index, (user, response) ->
                        // User message (collapsed by default)
                        div(classes = "message user collapsed hidden") {
                            attributes["data-index"] = "user-$index"
                            attributes["data-role"] = "user"
                            attributes["data-attempt-index"] = index.toString()
                            small(classes = "role-indicator") { i { +"User" } }
                            div(classes = "message-content") {
                                p { +user.content.orEmpty() }
                            }
                        }

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
                    // Final messages
                    finalMessages.forEach { msg ->
                        val role = msg.role.role
                        div(classes = "message $role hidden") {
                            attributes["data-role"] = role
                            small(classes = "role-indicator") { i { +role } }
                            // will be html content
                            unsafe{ + msg.content.orEmpty() }
                        }
                    }
                }
            }
            script {
                unsafe {
                    raw("""
                    const allMessages = document.querySelectorAll('.message');
                    let current = 1; // Start from 1 to skip the system message
                    
                    // Add click handlers for user messages
                    function setupUserMessageHandlers() {
                        document.querySelectorAll('.message.user.collapsed').forEach(userMsg => {
                            userMsg.addEventListener('click', function() {
                                this.classList.remove('collapsed');
                                this.classList.add('expanded');
                                
                                // Scroll to reveal the expanded content
                                const container = document.querySelector('.right');
                                this.scrollIntoView({ behavior: 'smooth', block: 'center' });
                            });
                        });
                    }
                    
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
                    
                    function showResultsGrid() {
                        const resultsGrid = document.getElementById('results-grid');
                        if (resultsGrid) {
                            resultsGrid.classList.remove('hidden');
                        }
                    }
                    
                    function showNext() {
                        if (current >= allMessages.length) {
                            // All messages have been shown, now show the results grid
                            setTimeout(showResultsGrid, 1000);
                            return;
                        }
                        const el = allMessages[current];
                        const role = el.getAttribute('data-role');
                        el.classList.remove('hidden');
                        
                        // Handle User messages differently
                        if (role === 'user') {
                            // User messages appear collapsed and clickable immediately
                            setupUserMessageHandlers();
                            setTimeout(() => {
                                el.scrollIntoView({ behavior: 'smooth', block: 'end' });
                            }, 100);
                            current++;
                            setTimeout(showNext, 600);
                            return;
                        }
                        
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
                        const contentP = el.querySelector(':scope > p');
                        if (!contentP) {
                            console.error('No content found for message:', el);
                            current++;
                            setTimeout(showNext, 800);
                            return;
                        }
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
                                
                                setTimeout(typeChar, 5);
                            } else {
                                if (role === 'reasoning') {
                                    // Get the attempt index directly from the message attribute
                                    const attemptIndex = parseInt(el.getAttribute('data-attempt-index'));
                                    // Show board tiles and guess message for this attempt
                                    showGuessMessage(attemptIndex);
                                    setTimeout(() => revealRowsForAttempt(attemptIndex), 500);
                                }
                                current++;
                                setTimeout(showNext, 1500);
                            }
                        }
                        typeChar();
                    }
                    
                    window.onload = showNext;
                 """
                    )
                }
            }

            // StatCounter tracking
            script(type = "text/javascript") {
                unsafe {
                    raw("""
                    var sc_project=12088911;
                    var sc_invisible=1;
                    var sc_security="8aad1c2e";
                    """)
                }
            }
            script(type = "text/javascript") {
                attributes["src"] = "https://www.statcounter.com/counter/counter.js"
                attributes["async"] = "true"
            }
            unsafe {
                raw("""<noscript><div class="statcounter"><a title="Web Analytics" href="https://statcounter.com/" target="_blank"><img class="statcounter" src="https://c.statcounter.com/12088911/0/8aad1c2e/1/" alt="Web Analytics" referrerPolicy="no-referrer-when-downgrade"></a></div></noscript>""")
            }
        }
    }

    // Write to local path
    val localHtmlFile = File(REPLAY_HTML_FILENAME)
    localHtmlFile.writeText(htmlContent)
    println("Replay saved to ${localHtmlFile.name}")

    // Upload to S3 if repository is available
    s3Repository?.let {
        it.uploadFile(HTML_FILENAME, localHtmlFile)
        println("HTML uploaded to S3")
    }
}
