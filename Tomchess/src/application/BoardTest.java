package application;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.Date;
import java.util.LinkedList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import application.Board.ScoredMove;

public class BoardTest {

	@Test
	public void testIsDarkSquare() {
		assertTrue(Board.isDarkSquare(0, 0));
		assertFalse(Board.isDarkSquare(0, 1));
	}

	@Test
	public void testEnpassant() throws Exception {
		Board b = new Board();
		//1 e4
		assertTrue(b.makeMoveByString("e2 - e4"));
		//1 ... e6
		assertTrue(b.makeMoveByString("e7 - e6"));
		//2 e5
		assertTrue(b.makeMoveByString("e4 - e5"));
		//2 ... f5
		assertTrue(b.makeMoveByString("f7 - f5"));
		//3 exf
		assertTrue(b.makeMoveByString("e5 x f6"));
		
		//our pawn has moved
		assertTrue(b.getPieceAtIndex(Board.squareNameToIndex("e5")).c == Color.EMPTY);
		
		assertTrue(b.getPieceAtIndex(Board.squareNameToIndex("f6")).p == Piece.PAWN);
		assertTrue(b.getPieceAtIndex(Board.squareNameToIndex("f6")).c == Color.WHITE);;

		assertTrue(b.getPieceAtIndex(Board.squareNameToIndex("f5")).c == Color.EMPTY);

	}
	
	@Test
	public void doPerftTests() throws Exception {
		final long maxCountToAttempt = 0;
		
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		DocumentBuilder builder = factory.newDocumentBuilder();
		
		Document doc = builder.parse(Main.class.getResourceAsStream("/data.xml"));
		
		System.out.println("ROot: " + doc.getDocumentElement().toString());
		long totalTime = 0L;
		long totalCount = 0L;
		
		NodeList nList = doc.getElementsByTagName("position");
		for (int i = 0; i < nList.getLength(); i++) {
			Element pos = (Element)nList.item(i);
			
			String fenString = pos.getElementsByTagName("fen").item(0).getTextContent();
			
			Board myBoard = new Board(fenString);
			System.out.println("====");
			System.out.print(myBoard.renderState(""));
			
			NodeList perftNodes = pos.getElementsByTagName("perft");
			for (int j = 0; j < perftNodes.getLength(); j++) {
				Element thisPerft = (Element)perftNodes.item(j);
				int depth = Integer.parseInt( thisPerft.getAttribute("depth") );
				long count = Long.parseLong( thisPerft.getAttribute("count") );
				
				if (maxCountToAttempt > 0 && count > maxCountToAttempt)
					continue;
				
				long startTime = System.currentTimeMillis();
				long elapsedTime = 0;
				assertEquals(count, myBoard.perft(depth));
				elapsedTime = Math.max(1, (new Date()).getTime() - startTime);			
				System.out.println("perft(\t" + depth + ") = \t" + count + " \t" + elapsedTime + "ms \t(" + count/elapsedTime + " kn/s)");
				
				totalTime += elapsedTime;
				totalCount += count;
			}			
		}
		System.out.println("Total \t" + totalCount + " \t in " + totalTime + "ms \t(" + totalCount/totalTime + " kn/s)");
		
	}
	
		
	@Test
	public void testGetLegalMoves() {
		//test perft count for opening position
		Board b = new Board();
		assertEquals(20, b.perft(1));
		assertEquals(8902, b.perft(3));
		assertEquals(197281, b.perft(4));
		
		//Promotion test position		
		Board c = new Board("n1n5/PPPk4/8/8/8/8/4Kppp/5N1N b");
		
		assertEquals(24, c.perft(1));
		assertEquals(496, c.perft(2));
		assertEquals(9483, c.perft(3));		
	}
	
	/* this was for hunting the bug that ended up being the delta-pruning margin sign error
	@Test
	public void doQuiTest() {
		Board b = new Board("r3k2r/4bpp1/p1np1nbp/q3p1P1/1p2P2P/1B1PBP2/NPPQN3/2KR3R b kq");
		b.settings.enableQuiescence = true;
		b.settings.maxQuiescencePly = 6;
		b.settings.deltaPruneMargin = 300;
		
		String[] inspectingLine = {"h6 x g5", "e3 x g5", "d6 - d5"};
		
		System.out.println("TImy searching on \n" +b.renderState(">>>"));			
		
		b.settings.killerMove = true;
		b.settings.inspectLine = inspectingLine; 

		b.getBestMoveAlphaBeta(4);
		
	}*/
	
	@Test
	public void testDeIndex() {
		for (int x = 0; x < 8; x++) {
			for (int y = 0; y < 8; y++ ) {
				int index = Board.index(x, y);
				int[] deInd = Board.deIndex(index);
				assertEquals(x, deInd[0]);
				assertEquals(y, deInd[1]);
			}
		}
	}
	@Test
	public void testFlipIndex() {
		for (int x = 0; x < 8; x++) {
			for (int y = 0; y < 8; y++ ) {
				int index = Board.index(x, y);
				assertEquals(index, Board.flipIndex(Board.flipIndex(index)));
				assertEquals(Board.index(x, 7-y), Board.flipIndex(index));
			}
		}		
	}
	
	private void testGetZobristKey_moveUnMoves(Board b) {
		long keyBefore = b.getRecalculatedZobristKey();
		assertEquals(b.getIncrementalZobristKey(), b.getRecalculatedZobristKey());
		
		LinkedList<Move> legalMoves = b.getLegalMoves();
		assertTrue(legalMoves.size() > 0);
		
		//test after move and unmove from start position
		for (Move m : legalMoves) {
			b.pushMove(m);
			long keyAfterPush = b.getRecalculatedZobristKey();
			
			assertNotEquals(keyAfterPush, keyBefore);
			assertEquals(b.getIncrementalZobristKey(), b.getRecalculatedZobristKey());
			
			b.popMove();			
			long keyAfterPushAndPop = b.getRecalculatedZobristKey();
			assertEquals(b.getIncrementalZobristKey(), b.getRecalculatedZobristKey());
			
			assertEquals(keyAfterPushAndPop, keyBefore);
			//System.err.println(keyBefore  +"->" + keyAfterPush + "->"+keyAfterPushAndPop);
		}				
	}
	
	@Test
	public void testGetZobristKey() {		
		testGetZobristKey_moveUnMoves(new Board());
		testGetZobristKey_moveUnMoves(new Board("3r1rk1/p3qppp/2bb4/2p5/3p4/1P2P3/PBQN1PPP/2R2RK1 w - - 0 1"));
		testGetZobristKey_moveUnMoves(new Board("8/pk1B4/p7/2K1p3/8/8/4Q3/8 w - -"));
		
		//test arriving at the same position by two routes
		Board b = new Board();
		
		//1. d4
		assertTrue(b.makeMoveByString("d2 - d4"));
		//1 	... d5
		assertTrue(b.makeMoveByString("d7 - d5"));
		//2. Nf3
		assertTrue(b.makeMoveByString("g1 - f3"));
		//2.	... Nc6	
		assertTrue(b.makeMoveByString("b8 - c6"));
		
		long keyRouteA = b.getIncrementalZobristKey();
		
		Board c = new Board();
		//1. Nf3
		assertTrue(c.makeMoveByString("g1 - f3"));
		//1.	... Nc6	
		assertTrue(c.makeMoveByString("b8 - c6"));		
		
		//2. d4
		assertTrue(c.makeMoveByString("d2 - d4"));
		//2 	... d5
		assertTrue(c.makeMoveByString("d7 - d5"));
		
		long keyRouteB = c.getIncrementalZobristKey();
		assertEquals(keyRouteA, keyRouteB);				
		
	}

	@Test
	public void testMakeMoveByString() {
		Board b = new Board();
		//Try 1. e4
		assertTrue(b.makeMoveByString("e2 - e4"));
		assertTrue(b.getPieceAt(4,3).p == Piece.PAWN);
		assertTrue(b.getPieceAt(4,3).c == Color.WHITE);
		assertTrue(b.getPieceAt(4,1).p == null);
		assertTrue(b.getPieceAt(4,1).c == Color.EMPTY);
				
		//Try black's 1 ... Nf6
		assertTrue(b.makeMoveByString("g8 - f6"));
		assertTrue(b.getPieceAt(5,5).p == Piece.KNIGHT);
		assertTrue(b.getPieceAt(5,5).c == Color.BLACK);
		assertTrue(b.getPieceAt(6,7).p == null);
		assertTrue(b.getPieceAt(6,7).c == Color.EMPTY);
		
		//Try white's 2 d3
		assertTrue(b.makeMoveByString("d2 - d3"));
		assertTrue(b.getPieceAt(3,2).p == Piece.PAWN);
		assertTrue(b.getPieceAt(3,2).c == Color.WHITE);
		assertTrue(b.getPieceAt(3,1).p == null);
		assertTrue(b.getPieceAt(3,1).c == Color.EMPTY);		

		//Try a dodgy move
		assertFalse(b.makeMoveByString("a6 - d7"));
		
		//Try black's 2 ... Nxe4
		assertTrue(b.makeMoveByString("f6 x e4"));
		assertTrue(b.getPieceAt(4,3).p == Piece.KNIGHT);
		assertTrue(b.getPieceAt(4,3).c == Color.BLACK);
		assertTrue(b.getPieceAt(5,5).p == null);
		assertTrue(b.getPieceAt(5,5).c == Color.EMPTY);					
	}
	
	@Test
	public void testDrawByRepetition() {
		
		Board b = new Board("6k1/6p1/8/6KQ/1r6/q2b4/8/8 w - -");
		
		for (int i = 0; i < 2; i++) {
			assertTrue(b.makeMoveByString("h5 - e8"));
			assertTrue(b.makeMoveByString("g8 - h7"));
			
			assertTrue(b.makeMoveByString("e8 - h5"));
			assertTrue(b.makeMoveByString("h7 - g8"));
		}
		Board.EvaluationResult eval = b.evaluatePosition();
		assertTrue(eval.outcome == EnumOutcome.DRAW_REPETITION);
	}
	
	@Test
	public void testIsCheckMate() {
		Board smotheredMate = new Board("6RK/5nPP/8/8/8/8/8/3k4 w - -");
		/*
			. . . . . . R K 
			. . . . . n P P 
			. . . . . . . . 
			. . . . . . . . 
			. . . . . . . . 
			. . . . . . . . 
			. . . . . . . . 
			. . . k . . . . 
			(white to move)
		 */
		assertTrue(smotheredMate.isCheckMate());				
	}

	@Test
	public void testGetBestMoveAlphaBeta() {
		
		//== Test to see behaviour at horizon after Nxp on this board.	
		//With quiescence on: the knight must not take pawn.
		//With quiescence off: the knight should take pawn.
		Board tastyPawnTarpBoard = new Board("rnbqkbnr/ppp1pppp/8/3p4/8/2N5/PPPPPPPP/R1BQKBNR w KQkq -");
		/* 
		 	r n b q k b n r 
			p p p . p p p p 
			. . . . . . . . 
			. . . p . . . . 
			. . . . . . . . 
			. . N . . . . . 
			P P P P P P P P 
			R . B Q K B N R 
			(white to move)
		 */	
		Board.ScoredMove sm;
		
		//Search to depth 1 with quiescence.
		tastyPawnTarpBoard.settings.enableQuiescence = true;
		sm = tastyPawnTarpBoard.getBestMoveAlphaBeta(1);
		
		System.out.println("with quiescence: " + sm.move.toString());
		assertNotEquals(sm.move.toString(), "c3 x d5");
						
		//search to depth 1 without quiescence.
		tastyPawnTarpBoard.settings.enableQuiescence = false;
		sm = tastyPawnTarpBoard.getBestMoveAlphaBeta(1);
		
		System.out.println("without quiescence: " + sm.move.toString());		
		assertEquals(sm.move.toString(), "c3 x d5");

		//== Test alpha-beta search for a checkmate in 5 half moves.
		/*
			. . . . . . . . 
			p k . B . . . . 
			p . . . . . . . 
			. . K . p . . . 
			. . . . . . . . 
			. . . . . . . . 
			. . . . Q . . . 
			. . . . . . . . 
			(white to move)
		*/
									  
		Board fivePlyMate = new Board("8/pk1B4/p7/2K1p3/8/8/4Q3/8 w - -");
		
		String[] ins = {};
		fivePlyMate.settings.inspectLine = ins;
		sm = fivePlyMate.getBestMoveAlphaBeta(5);
		assertEquals(sm.move.toString(), "d7 - h3");
		
		System.err.println(sm.move.toString());
		
	}
	
	

}
