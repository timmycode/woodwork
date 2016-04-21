package application;
	
import java.awt.Toolkit;
import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;

import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ListView;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.TextArea;
import javafx.scene.control.Button;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.media.AudioClip;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;


public class Main extends Application {
	Board startingBoard;
	
	GridPane grid;
	Button muhButtons[];
	
	VBox movesVBox;
	ListView<String> myListView;
	HBox moveControlsHBox;
	Button backMove;
	
	HBox bottomHBox;
	TextArea subText;
	VBox controlVBox;	
	Button fenLoadButton;
	Button writeFenButton;
	Button playButton;
	CheckBox checkComputerPlaysWhite, checkComputerPlaysBlack, checkFlipBoard;
	
	VBox engineBox;
	TextArea engineText, castleText;	
	
	
	Deque<Move> legalMoves;
	Board b;
	int selectedSquare = 0;
	
	void doAMove(Move m) {
		b.pushMove(m);
		
		try {
		    final URL resource = getClass().getResource("/movesound.mp3");
		    	
	        Media media = new Media(resource.toString());
	        MediaPlayer mp = new MediaPlayer(media);
	        mp.play();
		} catch (Exception e) {
			System.err.println(e.toString());
			Toolkit.getDefaultToolkit().beep();
		}
		
		 //Media media = new Media("file:///movesound.wav"); //replace /Movies/test.mp3 with your file
	       //MediaPlayer player = new MediaPlayer(media); 
	       //player.play();
		
		legalMoves = b.getLegalMoves();
		selectedSquare = -1;
		putBoardToButtons();
		
		if (this.checkComputerPlaysBlack.isSelected()) {
			if ((b.getToPlay() == Color.BLACK)) {
				this.doTest();				
			}
		}
		if (this.checkComputerPlaysWhite.isSelected()) {
			if ((b.getToPlay() == Color.WHITE)) {
				this.doTest();				
			}
		}
	}
	
	void displayEvalDebug() {
		Board.EvaluationResult base = b.evaluatePosition();
		int diff[] = new int[64];
		for (int x = 0; x < 8; x++) {
			for (int y = 0; y < 8; y++) {
				long mask = (1L << Board.index(x,y));
				ColoredPiece cp = b.getPieceAt(x, y);
				
				if (cp.p != Piece.KING){ 
					b.setPieceAt(mask, null, Color.EMPTY);
					Board.EvaluationResult eval = b.evaluatePosition();				
					b.setPieceAt(mask, cp.p, cp.c);
					diff[Board.index(x,y)] = base.score - eval.score;
				}
				
				
			 }
		}
		engineText.setText(Board.renderArray(diff));
	}
	
	void loadPositionFEN(String x) {
		startingBoard = new Board(x);
		b = new Board(x);
		
		legalMoves = b.getLegalMoves();
		selectedSquare = -1;
		putBoardToButtons();	
		
	}
	
	void doTest() {
		//mate in 1 halfmove
		//loadPositionFEN("4k3/1R6/R7/8/8/8/8/4K3 w - -");
		//mate in 3 halfmove
		//loadPositionFEN("4k3/8/1R6/R7/8/8/8/4K3 w - -");
		//mate in 5 halfmove
		//loadPositionFEN("8/6k1/8/R7/1R6/8/8/4K3 w - -");
		try {
			b.settings.enableQuiescence = true;
			b.settings.maxQuiescencePly = 6;
			b.settings.deltaPruneMargin = 300;
			
			String[] inspectingLine = {};
			//String[] inspectingLine = null;
			
			System.out.println("searching on \n" +b.renderState(">>>"));			
			
			b.settings.enableNullMoveAB = true;
			b.settings.killerMove = true;
			b.settings.inspectLine = inspectingLine;
			b.settings.hashing = false;
					
		    Board.ScoredMove bm = b.getBestMoveWithIterativeDeepening(800, 50);
			//Board.ScoredMove bm = b.getBestMoveAlphaBeta(3);
			
			String line = "";
			boolean first = true;		
			
			System.out.println(bm.line.toString());
			
			for (Move m : bm.line) {
				if (b.getToPlay() == Color.BLACK && first) {
					line += "\t...";
				} 					
				first = false;
				
				VerboseMove vm = new VerboseMove(m, b);
				b.pushMove(m);
				line += "\t" + vm.algebraic();
				if (b.getToPlay() == Color.WHITE) {
					line += ",\n";
				}
					
			}
			
			for (Iterator<Move> desc = bm.line.descendingIterator(); desc.hasNext();) {
				desc.next();
				b.popMove();
			}
			
			engineText.setText("[" + b.stats.quiescenceNodes + " Q-nodes]\t[" + b.stats.alphaBetaNodes + " AB-nodes]\n");
			if (bm.move != null) {
				engineText.setText("Move = " + bm.move.toString() + "\n" + "score = [" + bm.score + "]\ndepth = " + bm.depth + "\n[" + b.stats.evaluated + " evals]\n[" + b.stats.quiescenceNodes + " Q-nodes]\t[" + b.stats.alphaBetaNodes + " AB-nodes]\n[" + b.stats.hashHits + " hash hits]\t[" + b.stats.hashHints + "] hash hints\n[" + b.stats.nullMoveCutoffs + "] nullmove cutoffs\ntime = " + ((float)b.stats.elapsedTimeMS)/1000 + "\nline = \n " + line);
				if (  (checkComputerPlaysWhite.isSelected() && checkComputerPlaysBlack.isSelected()) == false ) { //doint get stick playing with itself...
					this.doAMove(bm.move);
				}
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			
		}
	}
	
	private class ButtonEventHandler implements EventHandler<ActionEvent>{
		@Override
		public void handle(ActionEvent evt) {
			// TODO Auto-generated method stub
			String button_id = ((Button)evt.getSource()).getId();
			
			if (button_id.compareTo("fenload") == 0) {
				String fenCode = subText.getText();
				
				loadPositionFEN(fenCode);
				return;
			}
			
			if (button_id.compareTo("backmove") == 0) {
				System.err.println("Goin back a move");
				if (b.getMoveHistory().size() > 0) {
					b.popMove();
					putBoardToButtons();
				}
				return;
			}
			if (button_id.compareTo("play") == 0) {
				doTest();
				return;
			}
			if (button_id.compareTo("writefen") == 0) {
				FenWriter fw = new FenWriter(b);
				System.err.println("FEN = " + fw.toString());
				subText.setText(fw.toString());
				return;
			}
			
			int squareIndex = Integer.parseInt(""+button_id);
			
			if (squareIndex == selectedSquare) {
				selectedSquare = -1;
			} else {
				
				if (selectedSquare != -1) {
					for (Move m : legalMoves) {
						if ((m.fromMask == (1L<<selectedSquare))&&(m.toMask == (1L<<squareIndex))) {
							doAMove(m);
							return;
						}
					}
				}
				
				selectedSquare = squareIndex;
				putBoardToButtons();
			}
			
			
			
			//System.out.println(String.format("move at %d",column));
		}
	}		
	
	void putButtonsinGrid(boolean flip) {
		grid.getChildren().clear();
		for (int x = 0; x < 8; x++) {
			for (int y = 0; y < 8; y++) {
				int i = Board.index(x, y);
				if (flip) {
					grid.add(muhButtons[i],7-x,y);
				} else {
					grid.add(muhButtons[i],x,7-y);
				}				 
				
			}
		}
	}
	
	@Override
	public void start(Stage primaryStage) {
		try {
			BorderPane border = new BorderPane();
			grid = new GridPane();
			
			grid.setHgap(1);
			grid.setVgap(1);
			
			engineText = new TextArea();
					
			selectedSquare = -1;
			
			movesVBox = new VBox();
			
			moveControlsHBox = new HBox();
			
			backMove = new Button("back");
			backMove.setId("backmove");
			backMove.setOnAction(new ButtonEventHandler());
			
			moveControlsHBox.getChildren().add(backMove);
			moveControlsHBox.setPrefHeight(100);
			
			myListView = new ListView<String>();
			myListView.setPrefHeight(800);
			movesVBox.getChildren().addAll(moveControlsHBox,myListView);
			
			grid.setPadding(new Insets(0, 0, 0, 0));
			engineText = new TextArea();
			engineText.setStyle("-fx-font-size:12pt; -fx-font-family:'Consolas', MONOSPACE");
			engineText.setText("...");
			engineText.setPrefWidth(400);
			engineText.setPrefHeight(700);
			

			subText = new TextArea();
			subText.setText("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
			
			bottomHBox = new HBox(0);
			this.controlVBox = new VBox(0);
			
			this.checkComputerPlaysBlack = new CheckBox("play Black");
			this.checkComputerPlaysWhite = new CheckBox("play White");
			this.checkFlipBoard = new CheckBox("flip board");
			
			writeFenButton = new Button("Write");
			writeFenButton.setId("writefen");
			writeFenButton.setOnAction(new ButtonEventHandler());
			
			fenLoadButton = new Button("Load");
			fenLoadButton.setId("fenload");
			fenLoadButton.setOnAction(new ButtonEventHandler());
			
			playButton = new Button("play");
			playButton.setId("play");
			playButton.setOnAction(new ButtonEventHandler());
			
			controlVBox.getChildren().add(fenLoadButton);
			controlVBox.getChildren().add(writeFenButton);
			controlVBox.getChildren().add(playButton);
			controlVBox.getChildren().add(checkComputerPlaysBlack);
			controlVBox.getChildren().add(checkComputerPlaysWhite);
			controlVBox.getChildren().add(checkFlipBoard);		
			
			
			bottomHBox.getChildren().add(subText);
			bottomHBox.getChildren().add(controlVBox);
			
			engineBox = new VBox(0);
			castleText = new TextArea();
			castleText.setStyle("-fx-font-size:12pt; -fx-font-family:'Consolas', MONOSPACE");
			castleText.setText("castling info");
			castleText.setPrefHeight(220);
			
			engineBox.getChildren().add(castleText );
			engineBox.getChildren().add(engineText);						
									
			muhButtons = new Button[64];
			for (int x = 0; x < 8; x++) {
				for (int y = 0; y < 8; y++) {
					int i = Board.index(x, y);
					muhButtons[i] = new Button("");
					muhButtons[i].setStyle("-fx-font: 30 arial; -fx-base: #cccccc;");
					muhButtons[i].setPrefSize(1000, 1000);
					muhButtons[i].setText( Board.squareName(i) );
					muhButtons[i].setOnAction(new ButtonEventHandler());
					muhButtons[i].setId("" + i);
					muhButtons[i].setTextOverrun(OverrunStyle.CLIP);					
				}
			}
			
			
			grid.setPrefSize(800, 800);
			int initialButtonSize = 100;
			grid.setPrefSize(initialButtonSize*7, initialButtonSize*6);
			
			b = new Board();
			startingBoard = new Board();
			//b.getBestMoveAlphaBeta(7);
						
			putBoardToButtons();
			
			border.setCenter(grid);	
			//border.setBottom(textField);
			border.setRight(engineBox);
			border.setPrefHeight(800);
			
			
			border.setBottom(bottomHBox);
			border.setLeft(movesVBox);
			
			Scene scene = new Scene(border);
			primaryStage.setScene( scene );
			scene.getStylesheets().add(getClass().getResource("application.css").toExternalForm());
			
			primaryStage.show();		
			
					
		} catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	
	private void putBoardToButtons() {
		putButtonsinGrid(this.checkFlipBoard.isSelected());
		String s = "";
		
		//displayEvalDebug();
		
		for (CastleMovePattern cmp : CastleMovePattern.values()) {
			if (b.canCastle(cmp)) {
				s += "CAN " + cmp.name() + "\n";
			} else {
				s += "CANNOT " + cmp.name() + "\n";
			}
		}		
		castleText.setText(s);
		
		ObservableList<String> items = FXCollections.observableArrayList ();
		
		ArrayList<VerboseMove> moveList = VerboseMove.getMoveHistoryVerbose(startingBoard, b.getMoveHistory());
		int k = 0;
		Move lastMove = null;
		for (VerboseMove m : moveList) {
			if (m.mover == Color.WHITE) {
				items.add("[" + ++k + "]\t" + m.algebraic());
			} else {
				items.add("...\t" + m.algebraic());
			}
			lastMove = m;
		}
		
		Board.EvaluationResult eval = b.evaluatePosition();
		items.add(eval.outcome.getDescription() + " (" + eval.score + ")"); 
		
		myListView.setItems(items);				
		
		legalMoves = b.getLegalMoves();
				
		for (int x = 0; x < 8; x++) {
			for (int y = 0; y < 8; y++) {
				int i = Board.index(x,y);
				ColoredPiece cp = b.getPieceAt(x, y);
				
				boolean isSelMoveTarget = false;
				boolean isSomeMoveTarget = false;
				for (Move m : legalMoves) {					
					if ( (m.toMask & (1L << i)) != 0) {
						isSomeMoveTarget = true;
						if (selectedSquare != -1) {
							if ( (m.fromMask & (1L << selectedSquare)) != 0 ) {
								isSelMoveTarget = true;
							}
						}
					}
				}
				
				boolean isPiece = false;
				
				if (cp.c == Color.WHITE || cp.c == Color.BLACK) {
					isPiece = true;					
				} 
				int fontSize = 20;
				if (isPiece) {
					fontSize = 34;
				}				
				
				String squareColor = (Board.isDarkSquare(x,y) ?  "#b58863" : "#f0d9b5"); 
				String squareColorHi = (Board.isDarkSquare(x,y) ? "#dac34a" : "#f7ec74");
				String fontstyle = "-fx-font: " + fontSize + " arial";
				
				//set the text
				if (isPiece == false) {					
					muhButtons[i].setText(Board.squareName(i));
				} 
				else if (cp.c == Color.WHITE) {
					muhButtons[i].setText(cp.p.unicodeWhite);
				} 
				else if (cp.c == Color.BLACK) {
					muhButtons[i].setText(cp.p.unicodeBlack);
				}
				
				String bgColor = squareColor;
				
				
				
				/*if (isSomeMoveTarget) {
					bgColor = "#aa5555";
				}*/
				
				if (cp.c == Color.WHITE) {					
					bgColor = "#ffffff";	
				} 
				else if (cp.c == Color.BLACK) {
					bgColor="#888888";
				}
 
				if (isSelMoveTarget) {
					bgColor = "#669999";				
				}
				else if (i == selectedSquare) {
					bgColor = "#99ffff";							
				} 
				
				if (lastMove != null) {
					if ((lastMove.toMask & (1L << Board.index(x,y))) != 0) {
						bgColor = "#ffffaa";
					}
					if ((lastMove.fromMask & (1L << Board.index(x,y))) != 0) {
						bgColor = "#cccc88";
					}					
				}				
				
				muhButtons[i].setStyle(fontstyle + ";  -fx-background-color:" + bgColor + ";");
			}
		}
	}
	
	public static void main(String[] args) {
		launch(args);
	}
}
