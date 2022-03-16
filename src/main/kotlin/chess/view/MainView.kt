package chess.view

import chess.board.*
import javafx.beans.value.ObservableValue
import javafx.event.EventHandler
import javafx.scene.canvas.Canvas
import javafx.scene.control.Label
import javafx.scene.layout.AnchorPane
import javafx.scene.paint.Color
import javafx.scene.paint.Color.*
import tornadofx.*

class MainView : View() {

    override val root : AnchorPane by fxml("/fxml/MainView.fxml")
    private val anchor : AnchorPane by fxid("anchor")
    private val boardCanvas : Canvas by fxid("boardCanvas")
    private val pieceCanvas : Canvas by fxid("pieceCanvas")
    private val mouseHighlightCanvas : Canvas by fxid("mouseHighlightCanvas")
    private val moveHighlightCanvas : Canvas by fxid("moveHighlightCanvas")
    private val whiteMoves : javafx.scene.control.ListView<String> by fxid("listView1")
    private val blackMoves : javafx.scene.control.ListView<String> by fxid("listView2")
    private val roundCounter: Label by fxid("roundCounter")

    private val xRatio: Double = 3.0/16.0
    private val yRatio: Double = 1.0/12.0

    private val boardMargin = 0.025
    private var margin = boardCanvas.width * boardMargin
    private var sizeActual = boardCanvas.width - boardCanvas.width * boardMargin * 2
    private var squareSize = sizeActual / 8
    private var mainBoard = initBoard()
    private var activeSide = PieceColor.WHITE
    private var activeSquare: IntArray? = null
    private var validMoves: Array<out IntArray>? = null

    private var turnStates: MutableMap<Int, Array<Array<Piece?>>> = mutableMapOf()
    private var turnCount: Int = 0

    private var magic: Boolean = false // forbidden variable


    init {

        //TODO CLEAN UP THIS FILE

        //currentStage?.isResizable = false
        currentStage?.minWidth = 800.0
        currentStage?.minHeight = 600.0

        // mouseHighlightCanvas.scaleY = mouseHighlightCanvas.scaleY * -1
        // moveHighlightCanvas.scaleY = moveHighlightCanvas.scaleY * -1
        // doesn't work here for some reason ¯\_(ツ)_/¯

        drawPieces(mainBoard, pieceCanvas, activeSide, boardMargin, squareSize)
        drawBoardBackground()

        turnStates[0] = getCopyOfBoard(mainBoard)

        anchor.widthProperty().addListener(ChangeListener {
            _: ObservableValue<out Number>?, _: Number, _: Number ->
            resizeActions()
        })

        anchor.heightProperty().addListener(ChangeListener {
            _: ObservableValue<out Number>?, _: Number, _: Number ->
            resizeActions()
        })


        pieceCanvas.onMouseClicked = EventHandler {

            //TODO create logic for situations where there is a check - it cant be that hard, can it? (lol)

            val coords = determineBoardCoords(it.x, it.y)

            if (coords[0] != -1 && coords[1] != -1) {

                var isPieceActiveColor = false
                if (checkCoords(coords)) {
                    isPieceActiveColor = getPiece(coords, mainBoard)?.color == activeSide
                }
                var validMove = false
                if (validMoves != null) { // check if the square clicked on would be a valid move
                    for (move in validMoves!!) {
                        if (move.contentEquals(coords)) {
                            validMove = true
                        }
                    }
                }

                if ((activeSquare == null  || !coords.contentEquals(activeSquare) && !validMove) && isPieceActiveColor) {
                    // if there is no active square, or if the selected square is not a valid move
                    wipeCanvas(moveHighlightCanvas)
                    fillMoves(filterPossibleMoves(coords, mainBoard))
                    setActiveSquare(coords)
                    validMoves = filterPossibleMoves(activeSquare!!, mainBoard)
                } else {
                    if (!activeSquare.contentEquals(coords)) {
                        if (validMoves != null) {
                            for (move in validMoves!!) {
                                if (coords.contentEquals(move)) {

                                    // save the board state so it can be reverted to
                                    turnStates[turnCount] = getCopyOfBoard(mainBoard)
                                    //printBoard(mainBoard) // keep in mind that the colors are inverted if you are debugging with this
                                    turnCount += 1
                                    roundCounter.text = "Round $turnCount"

                                    // move, draw pieces, undraw move highlights
                                    move(activeSquare!!, move, mainBoard)
                                    drawPieces(mainBoard, pieceCanvas, activeSide, boardMargin, squareSize)
                                    wipeCanvas(moveHighlightCanvas)
                                    flipActiveSide()

                                    // make sure these are empty, would lead to weird things happening
                                    activeSquare = null
                                    validMoves = null

                                }
                            }
                        }
                    }
                }
            }
        }

        pieceCanvas.onMouseMoved = EventHandler {

            if (!magic) {
                /**
                    very bad and inelegant solution
                    i wish i didn't have to do it like this but this is the only way i can change the scaleYs of the
                    canvases, because when i change them in the init block it just doesn't work
                */
                mouseHighlightCanvas.scaleY = mouseHighlightCanvas.scaleY * -1
                moveHighlightCanvas.scaleY = moveHighlightCanvas.scaleY * -1
                magic = true
            }

            wipeCanvas(mouseHighlightCanvas)
            val coords = determineBoardCoords(it.x, it.y)
            if (coords[0] != -1 && coords[1] != -1) {
                highlightSquare(mouseHighlightCanvas, coords, 4)
                //fillSquare(mouseHighlightCanvas, coords, rgb(0, 255, 125, 0.5))

                fillMoves(getSafeKingMoves(coords, mainBoard))
            }
        }

    } //end of init

    private fun setActiveSquare(coords: IntArray) {
        activeSquare = coords
        fillSquare(moveHighlightCanvas, coords, rgb(255, 255, 0, 0.5))
    }

    private fun flipActiveSide() {

        // flip the highlight layers along the Y axis
        mouseHighlightCanvas.scaleY = mouseHighlightCanvas.scaleY * -1
        moveHighlightCanvas.scaleY = moveHighlightCanvas.scaleY * -1

        activeSide =
        if (activeSide == PieceColor.WHITE) {
            PieceColor.BLACK
        } else {
            PieceColor.WHITE
        }
        drawPieces(mainBoard, pieceCanvas, activeSide, boardMargin, squareSize)
    }

    private fun fillMoves(moves: Array<out IntArray>?) {

        if (moves != null) {
            for (move in moves) {
                fillSquare(moveHighlightCanvas, move, rgb(0, 255, 125, 0.5))
            }
        }
        //fillSquare(moveHightlightCanvas, coords, rgb(255, 255, 0, 0.5))

    }

    private fun resizeActions() {
        scaleCanvas(boardCanvas)
        scaleCanvas(pieceCanvas)
        scaleCanvas(mouseHighlightCanvas)
        scaleCanvas(moveHighlightCanvas)
        drawBoardBackground()
        //drawPieces(mainBoard, pieceCanvas, activeSide, boardMargin, squareSize)
        update()
    }

    private fun determineBoardCoords(rawX: Double, rawY: Double, actual: Boolean = false): IntArray {
        /** returns the actual board coordinate from the raw coordinates from the click MouseEvent **/
        val xActual: Double = rawX - pieceCanvas.width * boardMargin
        val yActual: Double = rawY - pieceCanvas.height * boardMargin

        var xCoord: Int = -1
        var yCoord: Int = -1

        if (!(xActual / squareSize > 8 || yActual / squareSize > 8 || xActual / squareSize < 0 || yActual / squareSize < 0 && !actual)){
            if (activeSide == PieceColor.BLACK) {
                xCoord = (xActual / squareSize).toInt()
                yCoord = (yActual / squareSize).toInt()
            } else {
                xCoord = (xActual / squareSize).toInt()
                yCoord = 7 - (yActual / squareSize).toInt()
            }
        }

        return intArrayOf(xCoord, yCoord)
    }

    private fun highlightSquare(canvas: Canvas, coords: IntArray, highlightWidth: Int = 3) {
        if (checkCoords(coords)){
            val gCon = canvas.graphicsContext2D
            val origin = canvas.width * boardMargin
            gCon.fill = RED

            gCon.fillRect(origin + (coords[0] * squareSize) - highlightWidth,
                    origin + (coords[1] * squareSize) - highlightWidth,
                    squareSize + highlightWidth * 2,
                    squareSize + highlightWidth * 2)

            gCon.clearRect(origin + (coords[0] * squareSize), origin + (coords[1] * squareSize), squareSize, squareSize)
        }
    }
    //end of init

    private fun fillSquare(canvas: Canvas, coords: IntArray, highlightColor: Color) {
        if (coords[0] >= 0 && coords[1] >= 0){
            val gCon = canvas.graphicsContext2D
            val origin = canvas.width * boardMargin
            gCon.fill = highlightColor

            gCon.fillRect(origin + (coords[0] * squareSize),
                    origin + (coords[1] * squareSize),
                    squareSize,
                    squareSize)
        }
    }

    private fun wipeCanvas(canvas: Canvas) {
        canvas.graphicsContext2D.clearRect(0.0, 0.0, canvas.width, canvas.height)
    }

    private fun scaleCanvas(canvas: Canvas) {

        /** scales the passed canvas, making sure to not cause weird behaviour if one of them has a negative scale
         *  this also bypasses the need to redraw the pieces everytime **/

        val desiredWidth = anchor.width - (anchor.width * xRatio * 2)
        val desiredHeight = anchor.height - (anchor.height * yRatio * 2)

        val isYFlipped = canvas.scaleY < 0.0

        canvas.scaleX = desiredWidth / canvas.width
        canvas.scaleY = desiredHeight / canvas.height

        if (canvas.scaleX > canvas.scaleY) {
                canvas.scaleX = canvas.scaleY
        } else {
                canvas.scaleY = canvas.scaleX
        }

        if (canvas.scaleX < 0.0) {
            // just in case!
            canvas.scaleX *= -1.0
        }

        if (isYFlipped) {
            canvas.scaleY *= -1.0
        }
    }

    private fun update() {
        margin = boardCanvas.width * boardMargin
        sizeActual = boardCanvas.width - boardCanvas.width * boardMargin * 2
        squareSize = sizeActual / 8
    }


    fun testA() {

        //flipActiveSide()
        //mouseHighlightCanvas.scaleY = mouseHighlightCanvas.scaleY * -1
        //moveHighlightCanvas.scaleY = moveHighlightCanvas.scaleY * -1
        drawPieces(mainBoard, pieceCanvas, activeSide, boardMargin, squareSize)

        //fillMoves(getAllMovesForColor(PieceColor.BLACK, mainBoard).toTypedArray())


    }

    private fun drawBoardBackground() {

        margin = boardCanvas.width * boardMargin
        sizeActual = boardCanvas.width - boardCanvas.width * boardMargin * 2
        squareSize = sizeActual / 8

        val gCon = boardCanvas.graphicsContext2D

        gCon.fill = GREY
        gCon.fillRect(0.0, 0.0, boardCanvas.width, boardCanvas.height)
        //gCon.fill = WHITE
        //gCon.fillRect(margin, margin, boardBackground.width - 2 * margin, boardBackground.height - 2 * margin)

        for (x in 0..7) {
            for (y in 0..7) {
                if (gCon.fill == WHITE)
                    gCon.fill = GREY
                else gCon.fill = WHITE
                gCon.fillRect((x * squareSize) + margin,
                              (y * squareSize) + margin,
                              squareSize,
                              squareSize)
            }
            if (gCon.fill == WHITE)
                gCon.fill = GREY
            else gCon.fill = WHITE
        }
    }

    fun print() {
        println(currentStage?.width)
        println(currentStage?.height)
    }

    fun revertToRound(){
        if (turnCount > 0) {
            turnStates.remove(turnCount)
            turnCount -= 1
            mainBoard = turnStates[turnCount]!!
            roundCounter.text = "Round $turnCount"

            flipActiveSide()

            drawPieces(mainBoard, pieceCanvas, activeSide, boardMargin, squareSize)
        }

    }

}
