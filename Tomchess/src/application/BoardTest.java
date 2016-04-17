package application;

import static org.junit.Assert.*;

import org.junit.Test;

public class BoardTest {

	@Test
	public void testIsDarkSquare() {
		assertTrue(Board.isDarkSquare(0, 0));
		assertFalse(Board.isDarkSquare(0, 1));
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
		
		sm = fivePlyMate.getBestMoveAlphaBeta(5);
		assertEquals(sm.move.toString(), "d7 - h3");
		
		System.err.println(sm.move.toString());
		
	}

}
