@file:Suppress("TYPE_INFERENCE_ONLY_INPUT_TYPES_WARNING")package chess.viewimport chess.board.*import javafx.beans.value.ObservableValueimport javafx.collections.ObservableListimport javafx.event.EventHandlerimport javafx.geometry.Posimport javafx.scene.canvas.Canvasimport javafx.scene.control.Labelimport javafx.scene.image.Imageimport javafx.scene.layout.AnchorPaneimport javafx.scene.media.Mediaimport javafx.scene.media.MediaPlayerimport javafx.scene.paint.Colorimport javafx.scene.paint.Color.*import javafx.scene.text.Textimport javafx.stage.FileChooserimport tornadofx.*import java.awt.event.ActionListenerimport java.io.Fileimport java.lang.Exceptionimport javax.swing.Timerimport kotlin.random.Random@Suppress("NAME_SHADOWING")class MainView : View() {    override val root : AnchorPane by fxml("/fxml/MainView.fxml")    private val anchor : AnchorPane by fxid("anchor")    private val boardCanvas : Canvas by fxid("boardCanvas")    private val pieceCanvas : Canvas by fxid("pieceCanvas")    private val mouseHighlightCanvas : Canvas by fxid("mouseHighlightCanvas")    private val moveHighlightCanvas : Canvas by fxid("moveHighlightCanvas")    private val moveListView : javafx.scene.control.ListView<String> by fxid("listView1")    private val roundCounter: Label by fxid("roundCounter")    private val infoField: Text by fxid("infoField")    private val timerField: Text by fxid("timer")    /** graphical things **/    private val boardMargin = 0.030    private var squareSize = (boardCanvas.width - boardCanvas.width * boardMargin * 2) / 8    private var fillColor: Color = rgb(0, 255, 125, 0.5) // yellow    /** game logic **/    private var mainBoard = initBoard()    private var activeColor = PieceColor.WHITE    private var turnCount: Int = 0    private var turnStates: MutableMap<Int, Array<Array<Piece?>>> = mutableMapOf()    private var moveList: ObservableList<String> = observableList()    private var checkState: Boolean = false    private var castled: MutableMap<PieceColor, Boolean> = mutableMapOf(PieceColor.WHITE to false, PieceColor.BLACK to false)    private var castledOnTurn: MutableMap<PieceColor, Int> = mutableMapOf(PieceColor.WHITE to -1, PieceColor.BLACK to -1)    // has a side castled already? white - [0], black - [1]    private var lastPawnDoubleMove: IntArray? = null    private var computerOpponent = false    /** moves and coordinates of things **/    private var activeSquare: IntArray? = null    private var validMoves: Array<out IntArray>? = null    private var checkStateValidMoves: Array<out IntArray>? = null    private var magic: Boolean = false // forbidden variable    private var sound: Boolean = false    private var timerMap: MutableMap<PieceColor, Timer>    private var whiteTime = 0    private var blackTime = 0    init {        whiteTime = 60 * 20        val whiteTimer: Timer = Timer(1000) {            whiteTime -= 1            timerField.text = "White: ${getTimeFromSeconds(whiteTime)} \nBlack: ${getTimeFromSeconds(blackTime)}"        }        blackTime = 60 * 20        var blackTimer: Timer = Timer(1000) {            blackTime -= 1            timerField.text = "White: ${getTimeFromSeconds(whiteTime)} \nBlack: ${getTimeFromSeconds(blackTime)}"        }        timerMap = mutableMapOf(PieceColor.WHITE to whiteTimer, PieceColor.BLACK to blackTimer)        timerField.text = "White: ${getTimeFromSeconds(whiteTime)} \nBlack: ${getTimeFromSeconds(blackTime)}"        timerMap[PieceColor.WHITE]?.start()        moveListView.items = moveList        //currentStage?.isResizable = false        currentStage?.minWidth = 800.0        currentStage?.minHeight = 600.0        infoField.text = "${activeColor.name.toLowerCase()}'s turn"        // mouseHighlightCanvas.scaleY = mouseHighlightCanvas.scaleY * -1        // moveHighlightCanvas.scaleY = moveHighlightCanvas.scaleY * -1        // doesn't work here for some reason ¯\_(ツ)_/¯        drawPieces(mainBoard, pieceCanvas, activeColor, boardMargin)        drawBoardBackground(boardCanvas, WHITE, GRAY, boardMargin)        turnStates[0] = getCopyOfBoard(mainBoard)        anchor.widthProperty().addListener(ChangeListener {            _: ObservableValue<out Number>?, _: Number, _: Number ->            resizeActions()        })        anchor.heightProperty().addListener(ChangeListener {            _: ObservableValue<out Number>?, _: Number, _: Number ->            resizeActions()        })        pieceCanvas.onMouseClicked = EventHandler { it ->            val coords = determineBoardCoords(it.x, it.y)            checkState = checkForCheck(activeColor, mainBoard)            if (checkCoords(coords)) {                var isPieceActiveColor = false                isPieceActiveColor = getPiece(coords, mainBoard)?.color == activeColor                var validMove = false                if (validMoves != null) {                    for (move in validMoves!!) {                        if (move.contentEquals(coords)) { /** check if the square clicked on would be a valid move **/                            validMove = true                            break                        }                    }                }                if ((activeSquare == null  || !coords.contentEquals(activeSquare) && !validMove) && isPieceActiveColor) {                    playSound("pick.wav")                    /** if there is no active square, or if the selected square is not a valid move **/                    wipeCanvas(moveHighlightCanvas)                    setActiveSquare(coords)                    val piece = getPiece(coords, mainBoard)                    validMoves = if (piece?.type == PieceType.KING) {                        val safeKingMoves = getSafeKingMoves(activeSquare!!, mainBoard)                        safeKingMoves?.toTypedArray()                    } else {                        getPossibleMoves(activeSquare!!, mainBoard)                    }                    val actuallyValidMoves: ArrayList<IntArray> = arrayListOf()                    var replaceMoves = false                    if (checkState && piece?.type != PieceType.KING) { // the king moves are weird so this doesn't apply to them                        /** makes sure that if the kind is in check, only the moves that will uncheck him are possible **/                        checkStateValidMoves = getCheckResolvingMoves(activeColor, mainBoard)?.toTypedArray()                        for (move in validMoves!!) {                            if (checkStateValidMoves?.find { it.contentEquals(move)} != null ) {                                /** only add the move if it would resolve the check **/                                if (tryMoveOut(coords, move, activeColor, mainBoard)) {                                    actuallyValidMoves.add(move)                                }                            }                        }                        if (actuallyValidMoves.isEmpty()) {                            try {                                if (moveList[moveList.size - 1] != "++") {                                    moveList.add("++")                                    infoField.text = "${flipColor(activeColor)} wins"                                    showEndGameDialog()                                }                            } catch (e: ArrayIndexOutOfBoundsException) { }                        }                        replaceMoves = true                    }                    if (!castled[activeColor]!! && piece?.type == PieceType.KING) {                        val castleMoves = getCastleMoves(activeColor, mainBoard)                        for (move in arrayOf(castleMoves[0], castleMoves[1])) {                            if (move != null) {                                actuallyValidMoves.add(move)                            }                        }                    }                    /** en passant handling - does not work properly **/                    if (lastPawnDoubleMove != null && piece?.type == PieceType.PAWN) {                        val x = lastPawnDoubleMove!![0]                        if (activeSquare!![1] == 3 &&                            piece.color == PieceColor.BLACK &&                            activeSquare!![0] + 1 == x ||                            activeSquare!![0] - 1 == x) {                            actuallyValidMoves.add(lastPawnDoubleMove!!)                        } else if (activeSquare!![1] == 4 &&                            piece.color == PieceColor.WHITE &&                            activeSquare!![0] + 1 == x ||                            activeSquare!![0] - 1 == x) {                            actuallyValidMoves.add(lastPawnDoubleMove!!)                        }                    }                    validMoves =                        if (replaceMoves) {                            actuallyValidMoves.toTypedArray()                        } else {                            val tempValidMoves = validMoves?.toCollection(ArrayList())                            for (move in actuallyValidMoves) {                                tempValidMoves?.add(move)                            }                            tempValidMoves?.toTypedArray()                        }                    fillMoves(moveHighlightCanvas, validMoves, boardMargin, fillColor)                } else {                    /** not clicking on the active square, and validMoves are not null **/                    if (!activeSquare.contentEquals(coords) && validMoves != null) {                        for (move in validMoves!!) {                            if (coords.contentEquals(move)) {                                val piece = getPiece(activeSquare!!, mainBoard)                                val target = getPiece(coords, mainBoard)                                val moved: Boolean                                if (checkState && piece?.type != PieceType.KING) {                                    if (checkStateValidMoves?.find { it.contentEquals(move) } == null) {                                        moveList.add("++")                                        infoField.text = "$activeColor wins"                                        showEndGameDialog()                                        break                                    }                                }                                /** save the board state so it can be reverted to **/                                turnStates[turnCount] = getCopyOfBoard(mainBoard)                                roundCounter.text = "Round $turnCount"                                if (piece?.type == PieceType.PAWN) {                                    if (activeSquare!![1] - move[1] % 2 == 0) {                                        lastPawnDoubleMove = intArrayOf(move[0], move[1] - 1)                                    }                                } else {                                    lastPawnDoubleMove = null                                }                                /** make the move/moves in case of a castling move **/                                val castleMoves = getCastleMoves(activeColor, mainBoard)                                /*only the two first two elements are the moves of the king, searching in the others                                would cause wrong moves to be made*/                                if (arrayOf(castleMoves[0], castleMoves[1]).find { it.contentEquals(coords) } != null) {                                    if (coords.contentEquals(castleMoves[0])) {                                        move(activeSquare!!, move, mainBoard)                                        move(castleMoves[2]!!, castleMoves[3]!!, mainBoard)                                        moveList.add("0-0-0")                                        moved = true                                        playSound("move.wav")                                    } else {                                        move(activeSquare!!, move, mainBoard)                                        move(castleMoves[4]!!, castleMoves[5]!!, mainBoard)                                        moveList.add("0-0")                                        moved = true                                        playSound("move.wav")                                    }                                    castled[activeColor] = true                                    castledOnTurn[activeColor] = turnCount                                } else {                                    move(activeSquare!!, move, mainBoard)                                    moved = true                                        if (piece?.type == PieceType.PAWN) {                                            if (target != null) {                                                moveList.add(getReadableMove(activeSquare!!)[1] + "x" + getReadableMove(coords))                                                playSound("capture.wav")                                            } else {                                                moveList.add(getReadableMove(coords))                                                playSound("move.wav")                                            }                                        } else {                                            if (target != null) {                                                moveList.add(piece?.type.toString()[0] + "x" + getReadableMove(coords))                                                playSound("capture.wav")                                            } else {                                                moveList.add(piece?.type.toString()[0] + getReadableMove(coords))                                                playSound("move.wav")                                            }                                        }                                    if (checkForCheck(flipColor(activeColor), mainBoard)) {                                        moveList[moveList.size - 1] += "+"                                        playSound("check.wav")                                    }                                }                                if (!moved) {                                    moveList.add("++")                                    infoField.text = "${flipColor(activeColor)} wins"                                    showEndGameDialog()                                    break                                }                                /** draw pieces, undraw move highlights and active square and flip the active sides                                 *  and flip the timers being active **/                                timerMap[activeColor]?.stop()                                timerMap[flipColor(activeColor)]?.start()                                drawPieces(mainBoard, pieceCanvas, activeColor, boardMargin)                                wipeCanvas(moveHighlightCanvas)                                flipActiveSide()                                /** make sure these are empty, would lead to weird things happening **/                                activeSquare = null                                validMoves = null                                checkStateValidMoves = null                                turnCount += 1                                if (computerOpponent) {                                    makeRandomMove()                                }                                break                            }                        }                    }                }            }        }        pieceCanvas.onMouseMoved = EventHandler {            if (!magic) {                /** very bad and inelegant solution i wish i didn't have to do it like this but this is the only way                 *  i can change the scaleYs of the canvases, because when i change them in the init block it                 *  just doesn't work*/                mouseHighlightCanvas.scaleY = mouseHighlightCanvas.scaleY * -1                moveHighlightCanvas.scaleY = moveHighlightCanvas.scaleY * -1                magic = true            }            /** handles the currently hovered over square to be highlighted **/            infoField.text = "${activeColor.name.toLowerCase()}'s turn"            wipeCanvas(mouseHighlightCanvas)            val coords = determineBoardCoords(it.x, it.y)            if (checkCoords(coords)) {                highlightSquare(mouseHighlightCanvas, coords, squareSize, boardMargin)            }        }    } //end of init    private fun setActiveSquare(coords: IntArray) {        activeSquare = coords        fillMoves(moveHighlightCanvas, arrayOf(coords), boardMargin, rgb(255, 255, 0, 0.5))    }    private fun flipActiveSide() {        /** flip the highlight layers along the Y axis **/        /*        mouseHighlightCanvas.scaleY = mouseHighlightCanvas.scaleY * -1        moveHighlightCanvas.scaleY = moveHighlightCanvas.scaleY * -1         */        /** flip the colors **/        activeColor =        if (activeColor == PieceColor.WHITE) {            PieceColor.BLACK        } else {            PieceColor.WHITE        }        drawPieces(mainBoard, pieceCanvas, activeColor, boardMargin)    }    private fun resizeActions() {        /** scale canvases, redraw the background, and update all the properties **/        scaleCanvas(boardCanvas, anchor)        scaleCanvas(pieceCanvas, anchor)        scaleCanvas(mouseHighlightCanvas, anchor)        scaleCanvas(moveHighlightCanvas, anchor)        drawBoardBackground(boardCanvas, WHITE, GRAY, boardMargin)        //drawPieces(mainBoard, pieceCanvas, activeSide, boardMargin, squareSize)    }    private fun determineBoardCoords(rawX: Double, rawY: Double, actual: Boolean = true): IntArray {        /** returns the actual board coordinate from the raw coordinates from the click MouseEvent **/        val xActual: Double = rawX - pieceCanvas.width * boardMargin        val yActual: Double = rawY - pieceCanvas.height * boardMargin        var xCoord: Int        var yCoord: Int        xCoord = (xActual / squareSize).toInt()        yCoord = 7 - (yActual / squareSize).toInt()        return intArrayOf(xCoord, yCoord)    }    //end of init    private fun wipeCanvas(canvas: Canvas) {        canvas.graphicsContext2D.clearRect(0.0, 0.0, canvas.width, canvas.height)    }    private fun getTimeFromSeconds(time: Int): String {        var seconds = 0        var minutes = 0        seconds = time        if (seconds > 59) {            minutes = (seconds / 60).toInt()            seconds = time - minutes * 60        }        if (minutes < 10 && seconds < 10) {            return "0$minutes:0$seconds"        }        if (minutes < 10 && seconds >= 10) {            return "0$minutes:$seconds"        }        if (minutes >= 10 && seconds < 10) {            return "$minutes:0$seconds"        }        return "$minutes:$seconds"    }    fun showEndGameDialog() {        playSound("clapping.wav")        dialog(builder = {            label {                text = "${flipColor(activeColor)} Won"                alignment = Pos.CENTER            }            button {                text = "Start a new game"                onAction = EventHandler {                    newGame()                    close()                }            }        })?.show()    }    fun testA() {        playSound("clapping.wav")    }    fun revertToRound(){        if (turnCount > 0) {            if (castledOnTurn[flipColor(activeColor)]!! <= turnCount) {                castled[flipColor(activeColor)] = false                castledOnTurn[flipColor(activeColor)] = -1            }            turnStates.remove(turnCount)            turnCount -= 1            if (turnStates[turnCount] == null){                turnCount += 1                return            }            timerMap[activeColor]?.stop()            timerMap[flipColor(activeColor)]?.start()            mainBoard = turnStates[turnCount]!!            roundCounter.text = "Round $turnCount"            activeSquare = null            validMoves = null            checkStateValidMoves = null            wipeCanvas(moveHighlightCanvas)            moveList.removeAt(moveList.size - 1)            flipActiveSide()            infoField.text = "${activeColor.name.toLowerCase()}'s turn"            drawPieces(mainBoard, pieceCanvas, activeColor, boardMargin)        }    }    fun newGame() {        if (activeColor == PieceColor.BLACK) {            flipActiveSide()        }        infoField.text = "${activeColor.name.toLowerCase()}'s turn"        mainBoard = initBoard()        activeColor = PieceColor.WHITE        turnCount= 0        turnStates= mutableMapOf()        checkState= false        castled = mutableMapOf(PieceColor.WHITE to false, PieceColor.BLACK to false)        castledOnTurn = mutableMapOf(PieceColor.WHITE to -1, PieceColor.BLACK to -1)        lastPawnDoubleMove = null        whiteTime = 60 * 20        blackTime = 60 * 20        timerMap[PieceColor.WHITE]?.start()        timerMap[PieceColor.BLACK]?.stop()        moveList = observableList()        moveListView.items = moveList        /** moves and coordinates of things **/        activeSquare = null        validMoves = null        checkStateValidMoves = null        drawPieces(mainBoard, pieceCanvas, activeColor, boardMargin)    }    fun loadGame() {        val fileChooser = FileChooser()        val extensionFilter = FileChooser.ExtensionFilter("Text files (*.txt)", "*.txt")        fileChooser.extensionFilters.add(extensionFilter)        fileChooser.title = "Choose save location"        val file = fileChooser.showOpenDialog(currentStage) ?: return        val lines = file.readLines() // the save file is pretty small, therefore i can just use readLines()        for (rawLine in lines) {            var line = rawLine.split(":")            when (line[0]) {                "activeColor" -> {                    if (activeColor != getColorFromString(line[1])!!) {                        flipActiveSide()                    }                    activeColor = getColorFromString(line[1])!!                }                "turnCount" -> turnCount = line[1].toInt()                "checkState" -> checkState = line[1].toBoolean()                "whiteCastled" -> castled[PieceColor.WHITE] = line[1].toBoolean()                "blackCastled" -> castled[PieceColor.BLACK] = line[1].toBoolean()                "whiteCastledOnTurn" -> {                    try {                        castledOnTurn[PieceColor.WHITE] = line[1].toInt()                    } catch (e: java.lang.NumberFormatException) {                        castledOnTurn[PieceColor.WHITE] = -1                    }                }                "blackCastledOnTurn" -> {                    try {                        castledOnTurn[PieceColor.BLACK] = line[1].toInt()                    } catch (e: java.lang.NumberFormatException) {                        castledOnTurn[PieceColor.BLACK] = -1                    }                }                "lastPawnDoubleMove" -> {                    val splitLine = line[1].split("-")                    lastPawnDoubleMove?.set(0, splitLine[1].toInt())                    lastPawnDoubleMove?.set(1, splitLine[2].toInt())                }                "whiteTime" -> { whiteTime = line[1].toInt() }                "blackTime" -> { blackTime = line[1].toInt() }                "board" -> {                    val rows = line[1].split("/")                    val inputBoard: ArrayList<ArrayList<Piece?>> = arrayListOf()                    /** first get the strings into the inputBoard **/                    for (row in rows) {                        if (row == "") {                            break                        }                        val rowList: ArrayList<Piece?> = arrayListOf()                        for (piece in row.split("|")) {                            if (piece == "") {                                break                            }                            rowList.add(getPieceFromString(piece))                        }                        inputBoard.add(rowList)                    }                    val fakeBoard = Array(8) { Array<Piece?>(8) { null } }                    for (i in 0..7) {                        for (j in 0..7) {                            fakeBoard[i][j] = inputBoard[i][j]                        }                    }                    mainBoard = fakeBoard                }            }        }        timerField.text = "White: ${getTimeFromSeconds(whiteTime)} \nBlack: ${getTimeFromSeconds(blackTime)}"        roundCounter.text = "Round $turnCount"        drawPieces(mainBoard, pieceCanvas, activeColor, boardMargin)        timerMap[activeColor]?.start()        timerMap[flipColor(activeColor)]?.stop()    }    fun saveGame() {        val fileChooser = FileChooser()        val extensionFilter = FileChooser.ExtensionFilter("Text files (*.txt)", "*.txt")        fileChooser.extensionFilters.add(extensionFilter)        fileChooser.title = "Choose save location"        val file = fileChooser.showSaveDialog(currentStage) ?: return        /** the file structure will be: name:value so that things that should be multiline can just split the whole         *  string - especially the board (the file looks horrible though)**/        var inputString = ""            inputString += "activeColor:$activeColor:\n"            inputString += "turnCount:$turnCount:\n"            inputString += "checkState:$checkState:\n"            inputString += "whiteCastled:${castled[WHITE]}:\n"            inputString += "blackCastled:${castled[BLACK]}:\n"            inputString += "whiteCastledOnTurn:${castledOnTurn[WHITE]}:\n"            inputString += "blackCastledOnTurn:${castledOnTurn[BLACK]}:\n"            inputString += "lastPawnDoubleMove:${lastPawnDoubleMove?.get(0)}" + "-" +                            "${lastPawnDoubleMove?.get(1)}:\n"            inputString += "whiteTime:$whiteTime:\n"            inputString += "blackTime:$blackTime:\n"            inputString += "board:"            for (row in mainBoard) {                var subString = ""                for (piece in row) {                    subString += piece.toString() + "|"                }                inputString += "$subString/"            }        file.writeText(inputString)    }    fun makeRandomMove() {        /** this is just a proof of concept that the system is somehow capable of handling this, it would need a lot         *  of polish to get it working properly, and probably a small rewrite of the game logic into seperate functions         *  but alas, this is how its probably going to stay, sadly **/        for (i in 0..42) { //todo make it do a king move or something like that when in check            var moves: Array<out IntArray>? = null            val position = intArrayOf(Random.nextInt(0, 7), Random.nextInt(0, 7))            val piece = getPiece(position, mainBoard)            if (piece != null) {                if (piece.color == activeColor) {                    moves = getPossibleMoves(position, mainBoard)                }            }            if (moves != null) {                if (moves.isNotEmpty()) {                    val randMove = moves.random()                    if (checkCoords(randMove)) {                        if (tryMoveOut(position, randMove, activeColor, mainBoard)) {                            move(position, randMove, mainBoard)                            timerMap[activeColor]?.stop()                            timerMap[flipColor(activeColor)]?.start()                            drawPieces(mainBoard, pieceCanvas, activeColor, boardMargin)                            wipeCanvas(moveHighlightCanvas)                            flipActiveSide()                            activeSquare = null                            validMoves = null                            checkStateValidMoves = null                            turnCount += 1                            break                        }                    }                }            }            if (i == 42) {                showEndGameDialog()            }        }    }    fun computerOpponentToggle() {        computerOpponent = !computerOpponent    }    fun toggleSound() {        sound = !sound    }    fun playSound(fileName: String) {        if (sound) {            try {                val mediaPlayer = MediaPlayer(Media(File("src/main/resources/sound/$fileName").toURI().toString()))                mediaPlayer.play()                return            } catch (e: Exception) { }            val mediaPlayer = MediaPlayer(Media(File("sound/$fileName").toURI().toString()))            mediaPlayer.play()            /**            val pathString: String =                    if (Media(File("src/main/resources/sound/$fileName").toURI().toString()).error != null) {                        "sound/"                    } else {                        "src/main/resources/sound/"                    }            val mediaPlayer = MediaPlayer(Media(File("$pathString$fileName").toURI().toString()))            mediaPlayer.play()            **/        }    }}