package application;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedList;
import java.util.Random;
import java.util.Iterator;
import java.util.LinkedList;

import application.Board.ScoredMove;
import application.Board.SearchStatistics;

final public class Board {
	public static boolean isDarkSquare(int x , int y) {
		return ( (((x+y) % 2) == 0) ? true : false); 
	}
	final private class GameState {
		public GameState(GameState r) {
			super();
			this.white = new PieceBoards(r.white);
			this.black = new PieceBoards(r.black);
			this.zobrist = r.zobrist; 
			this.toPlay = r.toPlay;
			this.enPassantTarget = r.enPassantTarget;
		}
		
		public GameState() {
			this.white = new PieceBoards();
			this.black = new PieceBoards();
			this.zobrist = 0;
			this.toPlay = Color.WHITE;
			this.enPassantTarget = 0;
		}
		
		final private class PieceBoards {
			public PieceBoards(PieceBoards r) {
				super();
				this.pawns = r.pawns;
				this.knights = r.knights;
				this.bishops = r.bishops;
				this.rooks = r.rooks;
				this.queens = r.queens;
				this.kings = r.kings;
				this.all = r.all;
				this.kcastle = r.kcastle;
				this.qcastle = r.qcastle;
			}

			public PieceBoards() {
				super();
				this.pawns = 0;
				this.knights = 0;
				this.bishops = 0;
				this.rooks = 0;
				this.queens = 0;
				this.kings = 0;
				this.all = 0;
				this.kcastle = false; 
				this.qcastle = false;
			}			
			
			private long pawns, knights, bishops, rooks, queens, kings;
			private long all;
			private boolean kcastle,qcastle;
			
			public long getBoardForPiece(Piece p) {
				switch (p) {
					case PAWN:
						return pawns;
					case KNIGHT:
						return knights;
					case BISHOP:
						return bishops;
					case ROOK:
						return rooks;
					case QUEEN:
						return queens;
					case KING:
						return kings;
					default:
						return 0L;
				}					
			}
		}
		
		private long zobrist;
		PieceBoards white, black;
		Color toPlay;		
		private long enPassantTarget; //mask of squares which are valid move-targets for ep captures i.e. the square a pawn doublepushed through (0 if none)
		
		PieceBoards getBoardsForColor(Color c) {
			assert (c != Color.EMPTY);
			if (c == Color.WHITE) {
				return white;
			} else if (c == Color.BLACK) {
				return black;
			} else {
				return null;
			}
		}
		
		void doAndWithAllBoards(long allmask) {
			PieceBoards pb[] = { white, black };
			for (PieceBoards t : pb) {
				t.pawns &= allmask;
				t.knights &= allmask;
				t.bishops &= allmask;
				t.rooks &= allmask;
				t.queens &= allmask;
				t.kings &= allmask;
				t.all &= allmask;
			}
		}
		private void copyPieceAllBoards(long from, long to) {
			//System.err.println("FROM \n" + renderBitBoard(from));
			//System.err.println("TO \n" + renderBitBoard(to));			
			
			PieceBoards pb[] = { white, black };
			for (PieceBoards t : pb) {
				t.pawns 	|= ((t.pawns & from) != 0) ? to : 0L;
				t.knights 	|= ((t.knights & from) != 0) ? to : 0L;
				t.bishops 	|= ((t.bishops & from) != 0) ? to : 0L;
				t.rooks 	|= ((t.rooks & from) != 0) ? to : 0L;
				t.queens 	|= ((t.queens & from) != 0) ? to : 0L;
				t.kings 	|= ((t.kings & from) != 0) ? to : 0L;
				t.all		|= ((t.all & from) != 0) ? to : 0L;
			}  
		}

		public void updateZobrist() {
			// TODO Auto-generated method stub
			this.zobrist = h.getKey();			
		}
		public long getZobrist() {
			return this.zobrist;
		}
	};
	
	GameState b;
	
	//A standard test that returns number of moves in a position up to a given depth
	public long perft(int depth) {
		LinkedList<Move> legalMoves = this.getLegalMoves();
		if (depth == 1) {
			return legalMoves.size();
		}
		long count = 0L;
		
		for (Move m : legalMoves) {
			pushMove(m);
			count += perft(depth-1);
			popMove();			
		}
		return count;
	}
	
	public String renderState(String indentString) {
		String r = "";
		for (int y = 7; y >= 0; y--) {
			r += indentString;
			for (int x = 0; x < 8; x++) {
				ColoredPiece cp = this.getPieceAt(x,y);
				if (cp.c == Color.WHITE) {
					r += cp.p.fen.toUpperCase();
				} 
				else if (cp.c == Color.BLACK) {
					r += cp.p.fen.toLowerCase();					
				}
				else {
					r += ".";
				}
				r += " ";
			}
			r += "\n";
		}
		r += indentString;
		if (b.toPlay == Color.BLACK) {
			r += "(black to move)\n";
		} else {
			r += "(white to move)\n";
		}
		r += "\n";
		return r;
	}
	
	
	//precomputed bit arrays for move generation etc.
	private long[] knightAttacks; // [i] = bit mask of attacked squares for knight on square i
	private long[] kingAttacks;
	
	private long[] whitePawnSinglePushes; 
	private long[] whitePawnDoublePushes;
	
	private long[] blackPawnSinglePushes;
	private long[] blackPawnDoublePushes;
	private long allDoublePushSquares; //used for e.p. detection
	
	private long[] whitePawnCaps;
	private long[] blackPawnCaps;
	private long[] whitePawnCapsReverse;
	private long[] blackPawnCapsReverse;
	private long whitePromotionSquares;
	private long blackPromotionSquares;
		
	private long[][] rayAttacks; // [i][j] = bit mask of ray from square j in direction i
	private long fileFullMask[];
	
	//scoring squares
	private int pieceSquareMobility[][];
	private int squareScore[];
	private int pieceSquareScoreOpening[][];
	private int pieceSquareScoreEndgame[][];
	
	private int rookFileScore[];
		
	public static final class MaskIterator implements Iterator<Long> {
		private long mask;
		public MaskIterator(long _mask) {
			mask = _mask;
		}
		
		@Override
		public boolean hasNext() {
			return mask != 0;
		}
		
		@Override
		public Long next() {
			Long r = mask & ~(mask-1); 
			mask &= mask-1;
			return r;
		}

		@Override
		public void remove() {
			throw new UnsupportedOperationException();
		}
	}
	
	public static int index(int x, int y) {
		return y*8+x;
	}

	public static int[] deIndex(int i) {
		int r[] = new int[2];
		r[0] = i%8;
		r[1] = i/8;
		return r;
	}
	
	public static int flipIndex(int i) {
		int[] r = deIndex(i);
		return index(r[0], 7-r[1]);
	}
	
	public static String squareName(int index) {
		int y = index/8;
		int x = index%8;
		return "" + "abcdefgh".charAt(x) + (y+1);
	}
	
	public static int squareNameToIndex(String s) throws Exception {
		final String files ="abcdefgh";
		int x=0;
		for (int i = 0; i < 8; i++) {
			if (files.charAt(i) == s.charAt(0)) {
				x = i;
			}
		}
		int y =  Integer.parseInt( "" + s.charAt(1))-1;
		if ( Board.isWithinBoard(x,y) ) {
			int ind = index(x,y);
			System.err.println( squareName(ind) + " / " + s);
			if ( s.compareTo( squareName(ind) ) == 0) {
				return ind;
			} else {
				throw new Exception("Bad square name");
			}
		}
		else {
			throw new Exception("Bad square name");
		}
		
	}
	
	public static int slowMaskToIndex(long m) {
		for (int i = 0; i < 64; i++) {
			if ((m & (1L<<i))!=0) {
				return i;
			}
		}	
		return -1;
	}	

	private enum RayDirection {
		SW 	(0,	-1, -1),
		S 	(1,	0, 	-1),
		SE 	(2,	1,	-1),
		W 	(3,	-1,	0),
		E 	(4,	1,	0),
		NW 	(5,	-1,	1),
		N 	(6, 0,	1),
		NE 	(7,	1,	1);
		
		public int index, dx,dy,bitstep;
		public boolean isDiagonal() {
			return ((2+dx+dy)%2)== 0;
		}
		private RayDirection(int _index, int _dx, int _dy) {
			index = _index;
			dx = _dx;
			dy = _dy;
			bitstep = index(1+dx,1+dy) - index(1,1);
		}				
	}
	
	private static int countBitsBinary(long v) {
		int t = 0;
		while (v != 0) {
			v &= v-1;
			t += 1;
		}
		return t;	
	}
	
	public ColoredPiece getPieceAt(int x, int y) {
		long mask = 1L << index(x,y);
		return getPieceAtMask (mask);
	}
	public ColoredPiece getPieceAtIndex(int i) {
		return getPieceAtMask (1L << i);
	}
	public ColoredPiece getPieceAtMask(long mask) {
		ColoredPiece r = new ColoredPiece();
		GameState.PieceBoards t = null;
		if ( (b.white.all & mask) != 0 ) {
			r.c = Color.WHITE;
			t = b.white;
		} else if ( (b.black.all & mask) != 0 ) {
			r.c = Color.BLACK;
			t = b.black;
		} else {
			r.c = Color.EMPTY;
		}		
		
		if (t != null) {
			for (Piece p : Piece.values()) {
				if ( (t.getBoardForPiece(p) & mask) != 0 ) {
					r.p = p;
				}
			}
		}

		return r;
	}
	
	public long getEnpassantMask() {
		return b.enPassantTarget;
	}
	
	public static boolean isWithinBoard(int x, int y) {
		return (x >= 0 && x < 8 && y >= 0 && y < 8);
	}
	
	private Move attemptMakeMove(long from, long to) {	
		//System.out.println("from " + from + " to " + to);		
		if ( (to & (b.black.all | b.white.all)) == 0) {
			//empty square
			Move m = new Move(from ,to);
			return m;
		} else if ( ((b.white.all & from) != 0) && ((to & b.black.all) != 0) ) { //white taking black
			Move m = new Move(from, to);
			m.isCapture = true;
			return m;
		} else if ( ((b.black.all & from) != 0) && ((to & b.white.all) != 0) ) { //black taking white
			Move m = new Move(from, to);
			m.isCapture = true;
			return m;
		}
		return null;
	}
	
	final class MoveHistoryNode {
		Move m;
		GameState stateBefore;
	}
	private LinkedList<MoveHistoryNode> moveHistoryList;
	

	
	public boolean isCheckMate() {
		LinkedList<Move> leg = getLegalMoves();
		/*System.out.println("check = " + isCheck(b.toPlay));
		System.out.println("have moves :");
		for (Move m : leg) {
			System.out.println("  " + m.toString());
		}
		System.out.println("size = " + leg.size());
		*/
		if ((leg.size() == 0) && isCheck(b.toPlay)) {			
			return true;
		} else {
			return false;
		}
	}
	public void pushMove(Move m) {
		MoveHistoryNode mh = new MoveHistoryNode();
		mh.stateBefore = new GameState(b);
		mh.m = m;
		moveHistoryList.addFirst(mh);
		
		// en passant can only be done immediately after the double move. so we set to 0 here to disable it 
		this.b.enPassantTarget = 0;
		
		//This code does : "if it was a double pawn push then set the en passant target"
		if ( ((b.white.pawns | b.black.pawns) & m.fromMask ) != 0) {
			int fromIndex = maskToIndex(m.fromMask);

			//is it a double push away relative to the FROM-SQUARE?
			if ( (m.toMask & (whitePawnDoublePushes[fromIndex])) != 0 )
			{ 
				//if so set the en passant target to the single push from the FROM-SQUARE
				b.enPassantTarget = whitePawnSinglePushes[fromIndex];
			}
			
			//likewise for black
			if ( (m.toMask & (blackPawnDoublePushes[fromIndex])) != 0 )
			{ 
				b.enPassantTarget = blackPawnSinglePushes[fromIndex];
			}			
		}		

		if (m.isEnPassant()) {
			//clear target square on all boards
			//b.doAndWithAllBoards(~m.toMask);
			
			//copy pawn to new square
			b.copyPieceAllBoards(m.fromMask, m.toMask);
			b.zobrist ^= h.zobristDeltaForPieceAt( m.toMask );
			
			//clear from old square
			b.zobrist ^= h.zobristDeltaForPieceAt( m.fromMask );
			b.doAndWithAllBoards(~m.fromMask);
			
			//remvoe the pawn that was taken en passant
			b.zobrist ^= h.zobristDeltaForPieceAt( m.enPassantCap );
			b.doAndWithAllBoards(~m.enPassantCap);
		}
		else if (m.isCastle == true) {
			//For castle moves, we know the target squares are empty anyway. no need to clear them
			//copy king to new square
			b.copyPieceAllBoards(m.cm.KingFrom, m.cm.KingTo);
			b.zobrist ^= h.zobristDeltaForPieceAt( m.cm.KingTo );
			
			//clear old king square
			b.zobrist ^= h.zobristDeltaForPieceAt( m.cm.KingFrom );
			b.doAndWithAllBoards(~m.cm.KingFrom);
			
			//copy rook to new square
			b.copyPieceAllBoards(m.cm.RookFrom, m.cm.RookTo);
			b.zobrist ^= h.zobristDeltaForPieceAt( m.cm.RookTo );
			
			//clear old rook square
			b.zobrist ^= h.zobristDeltaForPieceAt( m.cm.RookFrom );
			b.doAndWithAllBoards(~(m.cm.RookFrom)); 			
			
		}
		else if (m.isPromotion == true) {
			//clear target square
			b.zobrist ^= h.zobristDeltaForPieceAt( m.toMask );
			b.doAndWithAllBoards(~m.toMask);				
			
			//create new piece at target square
			setPieceAt(m.toMask, m.promotionPiece, b.toPlay);
			b.zobrist ^= h.zobristDeltaForPieceAt( m.toMask );
			
			renderState("");
			//clear from square 
			b.zobrist ^= h.zobristDeltaForPieceAt( m.fromMask );
			b.doAndWithAllBoards(~m.fromMask);
		} else {
			
			//clear target square
			b.zobrist ^= h.zobristDeltaForPieceAt( m.toMask );
			b.doAndWithAllBoards(~m.toMask);
						
			//copy from old square
			b.copyPieceAllBoards(m.fromMask, m.toMask);
			b.zobrist ^= h.zobristDeltaForPieceAt( m.toMask );
			
			//clear from square
			b.zobrist ^= h.zobristDeltaForPieceAt( m.fromMask );
			b.doAndWithAllBoards(~m.fromMask);			
			
		}
		//Check castling rights are intact		
		
		if ( ((CastleMovePattern.WHITE_KING_SIDE.KingFrom & b.white.kings) == 0) || ((CastleMovePattern.WHITE_KING_SIDE.RookFrom & b.white.rooks) == 0) ) {
			if (b.white.kcastle) {
				b.white.kcastle = false;
				b.zobrist ^= h.zobristWhiteKCastle;
			}
		}		
		if ( ((CastleMovePattern.WHITE_QUEEN_SIDE.KingFrom & b.white.kings) == 0) || ((CastleMovePattern.WHITE_QUEEN_SIDE.RookFrom & b.white.rooks) == 0) ) {
			if (b.white.qcastle) {
				b.white.qcastle = false;
				b.zobrist ^= h.zobristWhiteQCastle;
			}
		}		
		if ( ((CastleMovePattern.BLACK_KING_SIDE.KingFrom & b.black.kings) == 0) || ((CastleMovePattern.BLACK_KING_SIDE.RookFrom & b.black.rooks) == 0) ) {
			if (b.black.kcastle) {
				b.black.kcastle = false;
				b.zobrist ^= h.zobristBlackKCastle;
			}
		}		
		if ( ((CastleMovePattern.BLACK_QUEEN_SIDE.KingFrom & b.black.kings) == 0) || ((CastleMovePattern.BLACK_QUEEN_SIDE.RookFrom & b.black.rooks) == 0) ) {
			if (b.black.qcastle) {
				b.black.qcastle = false;
				b.zobrist ^= h.zobristBlackQCastle;
			}
		}		
		
		//System.err.println("After");
		//System.err.println(renderBitBoard(b.white.queens));
		
		//test sanity
		if ((b.white.knights & (b.white.rooks | b.white.queens | b.white.bishops | b.white.pawns | b.white.kings)) != 0) {
			System.err.println("SANITY FAILED");
		}
		//next player's move
		b.toPlay = b.toPlay.Enemy();
		
		b.zobrist ^= h.zobristWhiteToMove;
		b.zobrist ^= h.zobristBlackToMove;
	}
	
	MoveHistoryNode popMove() {
		MoveHistoryNode mh = moveHistoryList.pop();
		b = mh.stateBefore;
		return mh;
	}
	
	void dumpTest() { 
		System.out.println(renderBitBoard(b.black.pawns));
		System.out.println(renderBitBoard(b.black.knights));
		System.out.println(renderBitBoard(b.black.bishops));
		System.out.println(renderBitBoard(b.black.rooks));
		System.out.println(renderBitBoard(b.black.queens));
		System.out.println(renderBitBoard(b.black.kings));
		
		System.out.println(renderBitBoard(b.white.pawns));
		System.out.println(renderBitBoard(b.white.knights));
		System.out.println(renderBitBoard(b.white.bishops));
		System.out.println(renderBitBoard(b.white.rooks));
		System.out.println(renderBitBoard(b.white.queens));
		System.out.println(renderBitBoard(b.white.kings));
		
	}

	
	private void addMoveToListIfNotIntoCheck(LinkedList<Move> theList, Move theMove, long pinnedPieces, boolean inCheckNow) {
		if (theMove == null) {
			return;
		}
		
		boolean safe = false;
		
		if ((inCheckNow == false) && theMove.isEnPassant() == false && ((theMove.fromMask & (b.black.kings|b.white.kings)) == 0)) {
			//a quick test for moves which could never put us in check
			//is this piece pinned to our king?
			if ( (theMove.fromMask & pinnedPieces) == 0 ) {
				safe = true;
			}
			/*
			long playerKing = b.getBoardsForColor(b.toPlay).kings;
			int playerKingSquare = maskToIndex(playerKing);
			long playerKingLines = this.getDiagonalAttacks(playerKingSquare,0L) | this.getOrthogonalAttacks(playerKingSquare,0L);
			if ((theMove.fromMask & playerKingLines) == 0) {
				safe = true;
			} else { //we are in line with the king. what about enemy sliding pieces?
				if ( (playerKingLines & (b.getBoardsForColor(b.toPlay.Enemy()).bishops | b.getBoardsForColor(b.toPlay.Enemy()).rooks | b.getBoardsForColor(b.toPlay.Enemy()).queens)) == 0) {
					//no enemy sliding pieces in line with the king . so he is safe
					safe = true;
				}
			}*/
		}
			
		if (safe == false) { //test manually
			pushMove(theMove);
			if ( isCheck( b.toPlay.Enemy() ) == false ) {
				safe = true;
			}
			popMove();
		}
		
		if (safe) {
			
			/*boolean manual = false;
			pushMove(theMove);
			if ( isCheck( b.toPlay.Enemy() ) == false ) {
				manual = true;
			}
			popMove();
			
			if (manual != safe) {
				System.err.println("Didnt work out here! For (" + theMove.toString() + ") on");
				System.err.println(this.renderState(""));
			}*/
			
			theList.add(theMove);
		}
	}
	
	//stuff for the reverse bitshift
	private int bitmaskSquareLookupTable[];
	private int bitmaskSquareLookupHash(long x) {
		long magic = 0xdcd60a423b3d175L; 
		return (int)((((x*magic)) >> 57) & ((1<<(7)) - 1));
	}
	public int maskToIndex(long mask) {
		return bitmaskSquareLookupTable[bitmaskSquareLookupHash(mask)];
	}
	
	public long getOrthogonalAttacks(int square, long occupancy) {
		return 		getRayAttacks(RayDirection.N,square,occupancy) 
				|	getRayAttacks(RayDirection.E,square,occupancy)
				|	getRayAttacks(RayDirection.S,square,occupancy)
				|	getRayAttacks(RayDirection.W,square,occupancy);
	}
	public long getDiagonalAttacks(int square, long occupancy) {
		return 		getRayAttacks(RayDirection.NE,square,occupancy) 
				|	getRayAttacks(RayDirection.NW,square,occupancy)
				|	getRayAttacks(RayDirection.SE,square,occupancy)
				|	getRayAttacks(RayDirection.SW,square,occupancy);
	}
	
	public long getRayAttacks(RayDirection d, int square, long occupancy) {
		//get ray attacks on the current board
		long maximal = rayAttacks[d.index][square];
		
		//check blockers		
		long capture = maximal & occupancy;
		if (capture == 0) {
			return maximal;
		} else {			
			long cp = 0;
	
			if (d.bitstep > 0) { //want least significant bit
				cp = capture ^ (capture&(capture-1));				
				return maximal & ((cp|(cp-1)));
			} else { //want most significant bit
				capture = Long.reverse(capture);
				cp = Long.reverse( capture ^ (capture&(capture-1)) );			

				return maximal & (cp | ~(cp-1));
			}			
		}		
	}
	
	private void initFromFEN(String fen) {
		h = new HashTable();
		b = new GameState();
		moveHistoryList = new LinkedList<MoveHistoryNode>(); 
		//make lookup table for bit mask -> square index  eg ...1000 -> 3, ...10 -> 1, etc.
		//ie reverse bit shift
		bitmaskSquareLookupTable = new int[128];

		for (int i = 0; i < 64; i++) {
			long bit = 1L << i;			
			bitmaskSquareLookupTable[bitmaskSquareLookupHash(bit)] = i;
		}	
		
		pieceSquareScoreOpening = new int[6][64];
		pieceSquareScoreEndgame = new int[6][64];
		squareScore = new int[64];
		//square scores
		for (int x = 0; x < 8; x++) {
			for (int y = 0; y < 8; y++) {
				int dx = Math.abs(x - 3) + Math.abs(x-4);  //4,3->1  ; 5,2 ->3 ; 6,1 -> 5 ; 7,0 -> 7
				int dy = Math.abs(y - 3) + Math.abs(y-4);
				int bonus = 20 / (dx*dx + dy*dy);
				squareScore[index(x,y)] = (7-dx) + (7-dy) + bonus; 
			}		
		}
		
		//System.out.println("Square scores:\n");
		//System.out.println(renderArray(squareScore));		
		
		//precomputed knight moves
		knightAttacks = new long[64];
		int knightMoves[][] = { {2,1}, {2,-1}, {-2,1}, {-2,-1}, {1,2}, {1,-2}, {-1,2}, {-1,-2} };
		
		for (int x = 0; x < 8; x++) {
			for (int y = 0; y < 8; y++) {
				int attackSq = 0;
				for (int offset[] : knightMoves) {
					int tx = x + offset[0];
					int ty = y + offset[1];
					if (isWithinBoard(tx, ty)) {
						knightAttacks[index(x,y)] |= (1L << index(tx,ty));
						attackSq += squareScore[index(tx,ty)];
					}
				}
				
				int penalty = 0;
				if (y == 0) { //backrank penalty to encourage development
					penalty = 13;
				}
				
				pieceSquareScoreOpening[Piece.KNIGHT.index][index(x,y)] = attackSq/4 - penalty;	//sum of centrality-value of attacked squares
				pieceSquareScoreEndgame[Piece.KNIGHT.index][index(x,y)] = attackSq/4;
			}		
		}
				
		//pre compute the king moves from each square
		kingAttacks = new long[64];		
		for (int x = 0; x < 8; x++) {
			for (int y = 0; y < 8; y++) {
				for (RayDirection d : RayDirection.values()) {
					int tx = x + d.dx;
					int ty = y + d.dy;
					if (isWithinBoard(tx,ty)) {
						kingAttacks[index(x,y)] |= (1L << index(tx,ty));
					}
				}
				int tweak = 0;
				if (y == 0) { //backrank bonus
					tweak += 20;
				}
				pieceSquareScoreOpening[Piece.KING.index][index(x,y)] = -squareScore[index(x,y)]*2 + tweak;	//far away from centre as possible
				
				//large penalty for king on the edge in endgame. this also helps to find distant but ez checkmates (eg K+R vs K).
				int edgePenalty = 0;
				if (x == 0 || y == 0 ||x == 7 || y == 7)
				{
					edgePenalty = 50;
				}
				pieceSquareScoreEndgame[Piece.KING.index][index(x,y)] = squareScore[index(x,y)]*3 - 30 - edgePenalty; //get to the centre !
			}
		}
			
		//pre-computed pawn pushes from each square
		whitePawnSinglePushes = new long[64];
		whitePawnDoublePushes = new long[64];
		
		blackPawnSinglePushes = new long[64];
		blackPawnDoublePushes = new long[64];
		
		allDoublePushSquares = 0;
		
		whitePawnCaps 	= new long[64];		
		blackPawnCaps   = new long[64];
		whitePawnCapsReverse = new long[64];
		blackPawnCapsReverse = new long[64];
		whitePromotionSquares = 0L;
		blackPromotionSquares = 0L;
		
		for (int x = 0; x < 8; x++) {
			//promotion squares			
			for (int y = 0; y < 8; y++) {				
				int i = index(x,y);
				
				if (y == 0) {
					blackPromotionSquares |= (1L << index(x,y));
				}
				if (y==7) {
					whitePromotionSquares |= (1L << index(x,y));					
				}
					
				if (isWithinBoard(x,y+1)) {
					whitePawnSinglePushes[i] |= (1L << index(x,y+1));
				}
				if (isWithinBoard(x,y-1)) {
					blackPawnSinglePushes[i] |= (1L << index(x,y-1));
				}
				
				//first-rank push			
				if (y == 1 && isWithinBoard(x,y+2)) {
					whitePawnDoublePushes[i] |= (1L << index(x,y+2));
				}
				if (y == 6 && isWithinBoard(x,y-2)) {
					blackPawnDoublePushes[i] |= (1L << index(x,y-2));
				}
				
				//white caps				
				if (isWithinBoard(x+1,y+1)) {
					whitePawnCaps[i] |= (1L << index(x+1,y+1));
					whitePawnCapsReverse[index(x+1,y+1)] |= (1L << index(x,y));
				}
				if (isWithinBoard(x-1,y+1)) {
					whitePawnCaps[i] |= (1L << index(x-1,y+1));
					whitePawnCapsReverse[index(x-1,y+1)] |= (1L << index(x,y));
				}
				
				//black pawn caps
				if (isWithinBoard(x+1,y-1)) {
					blackPawnCaps[i] |= (1L << index(x+1,y-1));
					blackPawnCapsReverse[index(x+1,y-1)] |= (1L << index(x,y)); 
				}
				if (isWithinBoard(x-1,y-1)) {
					blackPawnCaps[i] |= (1L << index(x-1,y-1));
					blackPawnCapsReverse[index(x-1,y-1)] |= (1L << index(x,y));
				}				
			}
		}
		
		//pre-compute the moves for direction d from each square
		rayAttacks = new long[8][64];
		//System.out.println("populating ray attacks");
		for (int x = 0; x < 8; x++) {
			for (int y = 0; y < 8; y++) {
				for (RayDirection d : RayDirection.values()) { //for each direction...
					rayAttacks[d.index][ index(x,y) ] = 0;
					
					//System.out.println(d.name() + " from square " + x + "," + y+ " : [dx,dy] = " + d.dx + ","  + d.dy + "]");
					//go along the ray from here
					for (int i = 1; i < 8; i++) {
						int tx = x + i * d.dx;
						int ty = y + i * d.dy;
						//System.out.println(  "" + tx + "," + ty ); 
						if (isWithinBoard(tx, ty)) {
							rayAttacks[d.index][ index(x,y) ] |= (1L << index(tx,ty)); 
						} else {
							break;
						}
					}
				}
			}
		}
				
		//bishop square scores
		for (int x = 0; x < 8; x++) {
			for (int y = 0; y < 8; y++) {
				long attacks = this.getDiagonalAttacks(index(x,y), 0);
				int total = 0;
				for (MaskIterator I = new MaskIterator(attacks); I.hasNext(); ) {
					long thisTarget = I.next();
					total += squareScore[maskToIndex(thisTarget)];
				}

				int penalty = 0;
				if (y == 0) { //backrank penalty to encourage development
					penalty = 28;
				}
				
				pieceSquareScoreOpening[Piece.BISHOP.index][index(x,y)] = (total / 4) - penalty;
				pieceSquareScoreEndgame[Piece.BISHOP.index][index(x,y)] = (total / 8); //still pretty useless to be on back rank in end game tbh
			}
		}
		
		//rook square scores
		for (int x = 0; x < 8; x++) {
			for (int y = 0; y < 8; y++) {
				int i=index(x,y);
				long attacks = this.getRayAttacks(RayDirection.N, i, 0) | this.getRayAttacks(RayDirection.S, i, 0);
				int total = 0;
				for (MaskIterator I = new MaskIterator(attacks); I.hasNext(); ) {
					long thisTarget = I.next();
					total += squareScore[maskToIndex(thisTarget)];
				}
				
				pieceSquareScoreOpening[Piece.ROOK.index][index(x,y)] = ((y==0)? 8 : -8); //stay on back rank please!
				pieceSquareScoreEndgame[Piece.ROOK.index][index(x,y)] = 0; // relax that
			}
		}
		
		final int _rookFileScore[] = {0, 1, 5, 10, 10, 5, 1, 0}; 
		rookFileScore =_rookFileScore;
		
		fileFullMask = new long[8];
		for (int x = 0; x < 8; x++) {
			long fileMask = 0L;
			for (int y = 0; y<8;y++) {
				fileMask = fileMask | (1L << index(x, y)); 
			}
			fileFullMask[x] = fileMask;
		}		
		
		//queen square scores
		for (int x = 0; x < 8; x++) {
			for (int y = 0; y < 8; y++) {
				int i=index(x,y);
				long attacks = this.getRayAttacks(RayDirection.N, i, 0) | this.getRayAttacks(RayDirection.S, i, 0);
				int total = 0;
				for (MaskIterator I = new MaskIterator(attacks); I.hasNext(); ) {
					long thisTarget = I.next();
					total += squareScore[maskToIndex(thisTarget)];
				}
				int backrankPenalty = 0;
				if (y==0 || y == 7)
					backrankPenalty = 10;
				pieceSquareScoreOpening[Piece.QUEEN.index][index(x,y)] =  - squareScore[index(x,y)] / 2 - backrankPenalty;
				pieceSquareScoreEndgame[Piece.QUEEN.index][index(x,y)] = ((total / 5) + squareScore[index(x,y)]*2) / 2;  //in end game not scared of being attacked. get central !
			}
		}		
		
		//pawn square scores
		for (int x = 0; x < 8; x++) {
			for (int y = 0; y < 8; y++) {
				int rankScoresOpen[] = {0,10,0,0,0,0,0,0};
				int rankScoresEnd[] = {0,10,11,12,20,40,70,0};
				
				int score = 0;
				for (MaskIterator S = new MaskIterator( whitePawnCaps[index(x,y)] ); S.hasNext();) {
					score += squareScore[maskToIndex(S.next())] / 3;
					score += squareScore[index(x,y)] / 2;
					if (x >= 6) score = rankScoresOpen[y];
					if (x <= 1) score = rankScoresOpen[y];
				}
				
				pieceSquareScoreOpening[Piece.PAWN.index][index(x,y)] = score;
				pieceSquareScoreEndgame[Piece.PAWN.index][index(x,y)] = rankScoresEnd[y];			 
			}
		}		
		
	
		
		for (Piece p : Piece.values()) {
			System.out.println(p.name() + " square scores:\n");
			System.out.println(renderArray(pieceSquareScoreOpening[p.index]));
		}
		
		//add the pieces
		loadFromFEN(fen);	
		b.updateZobrist();
		//System.out.println("Board loaded. Hash = " + String.format("%x", b.zobrist));
		settings = new SearchSettings();
	}
	
	public Board() {
		initFromFEN("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");		
	}
	public Board(String fen) {
		initFromFEN(fen);		
	}
	
	public static String renderBitBoard(long m) {
		String r = "";
		for (int y = 7; y>=0;y--) {
			for (int x = 0; x < 8; x++) {
				if ( (1L << (index(x,y)) &m) != 0) {
					r += "1 ";
				}
				else
				{
					r += ". ";
				}
			}
			r += "\n";
		}
		return r;
	}
	
	/*
	public int countRepetitions() {
		long keyNow = this.getZobristKey();
		for (MoveHistoryNode m : this.moveHistoryList) {
			m.stateBefore.getZobrist();
		}
	}*/

	public boolean isCheck(Color victim) {

		Color hunter = victim.Enemy();
		GameState.PieceBoards victimBoards = b.getBoardsForColor(victim);
		GameState.PieceBoards hunterBoards = b.getBoardsForColor(hunter);
		int ourKingIndex = maskToIndex(victimBoards.kings);

		if (hunter == Color.BLACK) {
			if ((blackPawnCapsReverse[ourKingIndex] & b.black.pawns) != 0) {
				return true;
			}
		}
		else if (hunter == Color.WHITE) {		
			if ((whitePawnCapsReverse[ourKingIndex] & b.white.pawns) != 0) {
				return true;
			}
		}
		
		//check knight moves
		if ((knightAttacks[ourKingIndex] & hunterBoards.knights) != 0) {
			return true;
		}
		//check king moves
		if ((kingAttacks[ourKingIndex] & hunterBoards.kings) != 0) {
			return true;
		}
	
		long diag = getDiagonalAttacks(ourKingIndex, b.white.all|b.black.all);		
		long ortho = getOrthogonalAttacks(ourKingIndex, b.white.all|b.black.all);
		//check bishops		
		if ( (diag & hunterBoards.bishops) != 0) {
			return true;
		}
		if ( (ortho & hunterBoards.rooks) != 0) {
			return true;
		}
		if ( ((diag | ortho) & hunterBoards.queens) != 0) {
			return true;
		}
		return false;
	}
	
	
	private void loadFromFEN(String v) {
		b = new GameState();
		
		int x = 0;
		int y = 7;	
		
		int read = 0;
	
		while ( isWithinBoard(x,y) ) {
			//read a square
			
			String c = ""+v.charAt(read);
			//System.out.println("Read '" + c + "' [" + c.length() +"]");
			int t = 1;
			if (c.compareTo("/") == 0) {
				t = 0;
			}
			else
			{
				//amount of squares to move cursor before next token				
				try {
					int skip = Integer.parseInt(c);
					t = skip;			
					if (t>0) {
						//System.out.println("Set piece at " + x + "," + y + " to empty");
						setPieceAt(x,y,null,Color.EMPTY);
					}
				}
				catch (Exception e) {
					for (Piece p : Piece.values()) {
						if ( c.compareTo( p.fen.toLowerCase() ) == 0 ) { //black piece
							setPieceAt(x,y,p,Color.BLACK);
							//System.out.println("Set piece at " + x + "," + y + " to " + p.name() + ",black");
						} else if ( c.compareTo( p.fen.toUpperCase() ) == 0 ) { //white piece
							setPieceAt(x,y,p,Color.WHITE);
							//System.out.println("Set piece at " + x + "," + y + " to " + p.name() + ",white");
						}
						
					}				
				}
			}
			//System.out.println("t="+t);
			//move the cursor
			while (t > 0) {
				x++;				
				if (x > 7) {
					x = 0;
					y--;
				}
				if (t > 1) {
					//System.out.println("Set piece at " + x + "," + y + " to empty");
					setPieceAt(x,y,null,Color.EMPTY);
				}				
				t--;
			}			
			
			//System.out.println("Now at ["+x+","+y+"]");
			//move to next character
			read++;			
		}
		
		b.toPlay = Color.EMPTY;
		//get the next b or w to see who is to play
		while (read < v.length()) {
			String c = ""+v.charAt(read);
			if (c.compareTo("w") == 0) {
				b.toPlay = Color.WHITE;
				break;
			} else if (c.compareTo("b") == 0) {
				b.toPlay = Color.BLACK;
				break;
			}
			read++;
		}
		assert(b.toPlay != Color.EMPTY);
		
		//now castling rights
		while (read < v.length()) {
			String c = ""+v.charAt(read);
			if (c.compareTo("K") == 0) {
				b.white.kcastle = true;
			} 
			else if (c.compareTo("Q") == 0) {
				b.white.qcastle = true;
			}
			else if (c.compareTo("k") == 0) {
				b.black.kcastle = true;
			}
			else if (c.compareTo("q") == 0) {
				b.black.qcastle = true;
			} 
			read++;
		}
	}
	
	private Move attemptConstructCastleMove(CastleMovePattern cm) {
		int thisKingSquare = maskToIndex(cm.KingFrom);
			
		long rays = getRayAttacks(RayDirection.E,thisKingSquare,b.white.all|b.black.all) | getRayAttacks(RayDirection.W,thisKingSquare,b.white.all|b.black.all);
		
		if ( (rays & cm.RookFrom) != 0) { //king can "see" rook, so nothing is blocking the castling			
			//try putting the king in the traverse square -> is he in check?							
			Move traverse = new Move(cm.KingFrom, cm.KingTraverse);
			pushMove(traverse);
			boolean traverseCheck = isCheck(b.toPlay.Enemy());
			popMove();
			
			if (traverseCheck == false) {
				Move m = new Move(cm.KingFrom, cm.KingTo);
				m.isCastle = true;
				m.cm = cm;
				return m;
			}
		}
		return null;			
	}
	
	public static String renderArray(int[] arr) {
		String s = "";
		for (int y = 7; y >= 0; y--) {
			for (int x = 0; x < 8; x++) {
				s += "" + arr[index(x,y)] + "\t";				
			}
			s += "\n";
		}
		return s;
	}
	
	private long getPiecesPinnedToPieceAt(long pinnedTo) {
		long pinnedPieces = 0L;
		Color hero = this.getPieceAtMask(pinnedTo).c;
		
		final int pinnedToSquare = maskToIndex(pinnedTo);		

		for (RayDirection d : RayDirection.values()) {
			final long threatPieces = b.getBoardsForColor(hero.Enemy()).queens | (d.isDiagonal() ? 
					b.getBoardsForColor(hero.Enemy()).bishops	
					: 	b.getBoardsForColor(hero.Enemy()).rooks		);
			//if no threats on the maximal ray then skip this direction
			if ((rayAttacks[d.index][pinnedToSquare] & threatPieces) == 0) 
				continue;
			//calculate the true ray
			final long ray = this.getRayAttacks(d, pinnedToSquare, b.white.all|b.black.all);
			if ((ray & b.getBoardsForColor(hero).all) != 0) {
				//our king sees one of our pieces along this ray
				//now trace again from the king, through the piece
				final long raySecond = this.getRayAttacks(d, pinnedToSquare, (b.white.all|b.black.all)& (~ray));
				if ( (raySecond & threatPieces) != 0 ) { //there's thread there pinning our piece!
					pinnedPieces |= ray & b.getBoardsForColor(hero).all;
				}
			}			
		}
		
		return pinnedPieces;
	}
	
	public LinkedList<Move> getLegalMoves() {
		final boolean inCheckNow = isCheck(b.toPlay);
	
		final long pinnedPieces = this.getPiecesPinnedToPieceAt( b.getBoardsForColor(b.toPlay).kings );
		LinkedList<Move> moves = new LinkedList<Move>();
		
		Piece promotionPieces[] = { Piece.QUEEN, Piece.KNIGHT, Piece.ROOK, Piece.BISHOP  };
				
		//pawn moves
		for (MaskIterator pawn = new MaskIterator(b.getBoardsForColor(b.toPlay).pawns); pawn.hasNext();) {
			long thisPawn = pawn.next();
			int thisPawnIndex = maskToIndex(thisPawn);
			
			long[] heroPawnSinglePushes = whitePawnSinglePushes;
			long[] heroPawnDoublePushes = whitePawnDoublePushes;
			long[] heroPawnCaps = whitePawnCaps;
			long heroPromotionSquares = whitePromotionSquares;
			if (b.toPlay == Color.BLACK) {
				heroPawnSinglePushes = blackPawnSinglePushes;
				heroPawnDoublePushes = blackPawnDoublePushes;
				heroPawnCaps = blackPawnCaps;
				heroPromotionSquares = blackPromotionSquares;
			}	
	
			if (b.enPassantTarget != 0) {
				if ((heroPawnCaps[thisPawnIndex] & b.enPassantTarget) != 0) //this pawn can move there
				{
					//what pawn is killed by this move?
					//its the one that just moved through our pawns MOVE-TO square
					Move m = attemptMakeMove(thisPawn, b.enPassantTarget);
					//find that by looking at a single pawn push from our pawns MOVE-TO square
					long[] enemyPawnSinglePushes = (b.toPlay == Color.WHITE) ? blackPawnSinglePushes : whitePawnSinglePushes;
					m.enPassantCap = enemyPawnSinglePushes[maskToIndex(b.enPassantTarget)];
					m.isCapture = true;
					addMoveToListIfNotIntoCheck(moves, m, pinnedPieces, inCheckNow);					
				}
			}
			
			/*
			System.err.println("BEFORE PAWNCLAL Toplay = " + b.toPlay + ",,");
			System.err.println("b.getBoardsForColor(b.toPlay) is ");
			System.err.println(this.renderBitBoard(b.getBoardsForColor(b.toPlay).knights));
			System.err.println("get for toplay is ");
			System.err.println(this.renderBitBoard(b.getBoardsForColor(b.toPlay).knights));
		
			System.err.println("blacks is ");
			System.err.println(this.renderBitBoard(b.black.knights));
			System.err.println("whites is ");
			System.err.println(this.renderBitBoard(b.white.knights));
			*/
			
			//Pushes and captures.
			for (MaskIterator I = new MaskIterator( heroPawnSinglePushes[thisPawnIndex] | heroPawnDoublePushes[thisPawnIndex] | heroPawnCaps[thisPawnIndex] ); I.hasNext();) {
				long target = I.next();
				Move m = attemptMakeMove(thisPawn, target);
				if (m != null) {
					//If it's a valid capture, or a valid push
					if ((m.isCapture == false && ((target& (heroPawnSinglePushes[thisPawnIndex]|heroPawnDoublePushes[thisPawnIndex])) != 0)) || (m.isCapture == true && ((target&heroPawnCaps[thisPawnIndex]) != 0))) {
						if ( (target & heroPromotionSquares) == 0 ) {
							//if double push must also have the single square empty
							if ((target & heroPawnDoublePushes[thisPawnIndex]) != 0) {
								if ((heroPawnSinglePushes[thisPawnIndex] & (b.white.all|b.black.all)) == 0) {									
									addMoveToListIfNotIntoCheck(moves, m, pinnedPieces, inCheckNow);
								}							
							}
							else	//single push doesnt care about that
							{
								addMoveToListIfNotIntoCheck(moves, m, pinnedPieces, inCheckNow);
							}
						} else { //promotion square so add possible promotions
							for (Piece p : promotionPieces) {
								Move a = attemptMakeMove(thisPawn, target);
								a.isPromotion = true;
								a.promotionPiece = p;
								addMoveToListIfNotIntoCheck(moves, a, pinnedPieces, inCheckNow);
							}							
						}
					}
				}
			}
			//En passant
		}
		/*
		System.err.println("Toplay = " + b.toPlay + ",,");
		System.err.println("b.getBoardsForColor(b.toPlay) is ");
		
		System.err.println(this.renderBitBoard(b.getBoardsForColor(b.toPlay).knights));
		System.err.println("blacks is ");
		System.err.println(this.renderBitBoard(b.black.knights));
		System.err.println("whites is ");
		System.err.println(this.renderBitBoard(b.white.knights));

		System.err.println();*/
		
		//knight moves
		for (MaskIterator knight = new MaskIterator(b.getBoardsForColor(b.toPlay).knights); knight.hasNext();) {
			long thisKnight = knight.next();			
			long thisKnightMoves = knightAttacks[ maskToIndex(thisKnight) ];
			for (MaskIterator I = new MaskIterator(thisKnightMoves); I.hasNext();) {
				Move m = attemptMakeMove(thisKnight, I.next());
				addMoveToListIfNotIntoCheck(moves, m, pinnedPieces, inCheckNow);
			}
		}
		//rook moves
		for (MaskIterator rook = new MaskIterator(b.getBoardsForColor(b.toPlay).rooks); rook.hasNext();) {
			long thisRook = rook.next();			
			long thisRookMoves = getOrthogonalAttacks(maskToIndex(thisRook), b.white.all|b.black.all);
			for (MaskIterator I = new MaskIterator(thisRookMoves); I.hasNext();) {
				Move m = attemptMakeMove(thisRook, I.next());
				addMoveToListIfNotIntoCheck(moves, m, pinnedPieces, inCheckNow);
			}			
		}
		//bishop moves
		for (MaskIterator bishop = new MaskIterator(b.getBoardsForColor(b.toPlay).bishops); bishop.hasNext();) {
			long thisBishop = bishop.next();
			//System.out.println("bishop from" + Board.squareName(maskToIndex(thisBishop)));
			//System.err.println(renderBitBoard(b.white.all|b.black.all));
			long thisBishopMoves = getDiagonalAttacks(maskToIndex(thisBishop), b.white.all|b.black.all);
		
			for (MaskIterator I = new MaskIterator(thisBishopMoves); I.hasNext();) {
				Move m = attemptMakeMove(thisBishop, I.next());
				addMoveToListIfNotIntoCheck(moves, m, pinnedPieces, inCheckNow);
			}			
		}		
		//queen moves
		for (MaskIterator queen = new MaskIterator(b.getBoardsForColor(b.toPlay).queens); queen.hasNext();) {
			long thisQueen = queen.next();	
			int thisQueenIndex = maskToIndex(thisQueen);
			
			//System.out.println("queen from" + Board.squareName(maskToIndex(thisQueen)));
			//System.err.println("E:\n" + renderBitBoard(getRayAttacks(RayDirection.E,maskToIndex(thisQueen), b.white.all|b.black.all)));
			
			long thisQueenMoves = getOrthogonalAttacks(thisQueenIndex, b.white.all|b.black.all) | getDiagonalAttacks(thisQueenIndex, b.white.all|b.black.all);
			//System.err.println("M:\n" + renderBitBoard(thisQueenMoves));
			
			for (MaskIterator I = new MaskIterator(thisQueenMoves); I.hasNext();) {
				Move m = attemptMakeMove(thisQueen, I.next());
				addMoveToListIfNotIntoCheck(moves, m, pinnedPieces, inCheckNow);
			}			
		}			
				
		//king moves
		for (MaskIterator king = new MaskIterator(b.getBoardsForColor(b.toPlay).kings); king.hasNext();) {
			long thisKing = king.next();	
			long thisKingMoves = kingAttacks[maskToIndex(thisKing)];

			//castling moves
			if (inCheckNow == false) {
				if ((thisKing & b.white.kings) != 0) { //is a white king
					if (b.white.kcastle) { //is white allowed castle kingside?	
						addMoveToListIfNotIntoCheck(moves, attemptConstructCastleMove(CastleMovePattern.WHITE_KING_SIDE), pinnedPieces, inCheckNow);
					}
					if (b.white.qcastle) { 	
						addMoveToListIfNotIntoCheck(moves, attemptConstructCastleMove(CastleMovePattern.WHITE_QUEEN_SIDE), pinnedPieces, inCheckNow);
					}				
				}
				else if ((thisKing & b.black.kings) != 0) { //is a black king
					if (b.black.kcastle) { 	
						addMoveToListIfNotIntoCheck(moves, attemptConstructCastleMove(CastleMovePattern.BLACK_KING_SIDE), pinnedPieces, inCheckNow);
					}
					if (b.black.qcastle) { 	
						addMoveToListIfNotIntoCheck(moves, attemptConstructCastleMove(CastleMovePattern.BLACK_QUEEN_SIDE), pinnedPieces, inCheckNow);
					}				
				}
			}
			
			for (MaskIterator I = new MaskIterator(thisKingMoves); I.hasNext();) {
				Move m = attemptMakeMove(thisKing, I.next());
				addMoveToListIfNotIntoCheck(moves, m, pinnedPieces, inCheckNow);
			}			
		}	
		
		return moves;
	}
	
	void setPieceAt(int x, int y, Piece p, Color c) {
		assert isWithinBoard(x,y);
		setPieceAt( 1L << index(x,y) ,p,c);
	}
	
	void setPieceAt(long m, Piece p, Color c) {		
		GameState.PieceBoards t = null;
		
		t = b.getBoardsForColor(c);
		//System.err.println("Sett at " +squareName(maskToIndex(m)));
		//clear this square
		b.doAndWithAllBoards(~m);

		if (t != null) {
			switch (p) {
			case PAWN:		
				t.pawns 	|= m; 
				break;
			case KNIGHT:	
				t.knights 	|= m;
				break;
			case BISHOP:	
				t.bishops 	|= m;
				break;
			case ROOK:
				t.rooks   	|= m;
				break;
			case QUEEN:
				t.queens 	|= m; 
				break;
			case KING:		
				t.kings 	|= m;
				break;
			default: //bad piece
				assert(false);
			}
			t.all |= m;
		}		
	}

	public boolean canCastle(CastleMovePattern c) {
		if (c == CastleMovePattern.WHITE_KING_SIDE) {
			return b.white.kcastle;
		} else if (c == CastleMovePattern.WHITE_QUEEN_SIDE) {
			return b.white.qcastle;
		} else if (c == CastleMovePattern.BLACK_KING_SIDE) {
			return b.black.kcastle;
		} else if (c == CastleMovePattern.BLACK_QUEEN_SIDE) {
			return b.black.qcastle;
		}
		return false;
	}

	public Color getToPlay() {
		return b.toPlay;
	}
	
	final class SearchSettings {
		boolean hashing = false;
		int maxQuiescenceChecks = 0;
		public int minHashDepth;
		public boolean enableQuiescence = true;
		public boolean killerMove = true;
		
		public boolean enableNullMoveAB = true;
		
		public int maxQuiescencePly = 6;
		public int deltaPruneMargin =300;
		
		public String[] inspectLine;
		public boolean inspectIntoQuiescence = false;
	}
	
	final class ScoredMove { 
		public Move move;
		public int score;
		public int depth;
		public boolean cutoff;
		public boolean standPat;
		public ScoredMove() {
			standPat = false;
		}
		public LinkedList<Move> line;
		
		final static public int GG = 10000;
		
		public ScoredMove(ScoredMove o) {
			this.move = o.move;
			this.score = o.score;
			this.depth = o.depth;
			this.cutoff = o.cutoff;
			this.standPat = o.standPat;
			
			this.line = new LinkedList<Move>();
			for (Move m : o.line) {
				this.line.add(new Move(m));
			}
		}
		
		
	}	
	
	public SearchSettings settings;
	
	final class SearchStatistics {
		public int maxDepth;
		public long elapsedTimeMS;
		public int evaluated;		
		
		public int quiescenceNodes = 0;
		public int alphaBetaNodes = 0;
		public int nullMoveCutoffs = 0;
		public int hashHits;
		public int hashHints;
	
		
	}
	
	public SearchStatistics stats;
	
	class EvaluationResult {
		int score;
		EnumOutcome outcome;
		public EvaluationResult(EnumOutcome _outcome, int _score) {
			score = _score;
			outcome = _outcome;			
		}
	}
	
	EvaluationResult evaluatePosition() {
		LinkedList<Move> legalMoves = this.getLegalMoves();
		return evaluatePosition(legalMoves);
	}
	
	//can be fed the list of legal moves manually in case we already calculated it
	EvaluationResult evaluatePosition(LinkedList<Move> legalMoves) {
		if (stats != null) {
			stats.evaluated += 1;
		}
		//test for checkmate and stalemate
		if (legalMoves.size() == 0) {
			if (isCheck(b.toPlay)) { 	//checkmate
				if (b.toPlay == Color.BLACK) {
					return new EvaluationResult(EnumOutcome.WHITE_WIN_CHECKMATE, ScoredMove.GG + (1000 - moveHistoryList.size()));
				}
				else {
					return new EvaluationResult(EnumOutcome.BLACK_WIN_CHECKMATE, -ScoredMove.GG - (1000 - moveHistoryList.size()));
				}
			} else {
				return new EvaluationResult(EnumOutcome.DRAW_STALEMATE, 0);				//stalemate
			}
		}
				
		//test for draw by repetition
		//we test for hashkeys that match the key 'right now' ie return true only when drawn on this very move
		int repeats = 0;
		for (MoveHistoryNode mh : this.moveHistoryList) {
			if (mh.stateBefore.zobrist == b.zobrist) {
				repeats += 1;
			}
		}
		if (repeats >= 2) {
			return new EvaluationResult(EnumOutcome.DRAW_REPETITION, 0);
		}

		//material considerations
		int material = 0;
		int totalNonPawnValue = 0;
		
		for (Piece p : Piece.values()) {
			final int countWhitePiecesOfThisType =  countBitsBinary(b.white.getBoardForPiece(p));
			final int countBlackPiecesOfThisType =  countBitsBinary(b.black.getBoardForPiece(p));
			
			material += countWhitePiecesOfThisType * p.naiveValue * 100;
			material -= countBlackPiecesOfThisType * p.naiveValue * 100;
			
			if (p != Piece.PAWN) {
				totalNonPawnValue += (countWhitePiecesOfThisType + countBlackPiecesOfThisType) * p.naiveValue * 100;
			}
		}
		
		//How much in the endgame are we?
		//At this material threshold we will be 100% endgame
		final int thresholdArriveEndgame = (2*Piece.ROOK.naiveValue)*100;
		//At this threshold we start considering endgame (linearly interpolate)
		final int thresholdBeginToLeaveOpening = (2*Piece.ROOK.naiveValue + 4*Piece.BISHOP.naiveValue)*100;

		//default to 100% opening  0% endgame
		int openingPercent = 100;
		int endgamePercent = 0;
		
		if (totalNonPawnValue <= thresholdArriveEndgame	)
		{
			openingPercent = 0;
			endgamePercent = 100;					
			//System.err.println("FullEND : Total nonpawn value=" +  totalNonPawnValue);
			//System.err.println("[o,e] = [" + openingPercent + "," + endgamePercent + "]"+ " thresholds are " + thresholdBeginToLeaveOpening + "," + thresholdArriveEndgame);

		} else if (totalNonPawnValue > thresholdBeginToLeaveOpening) {
			openingPercent = 100;
			endgamePercent = 0;			
			//System.err.println("FULLOPEN: Total nonpawn value=" +  totalNonPawnValue + " thresholds are " + thresholdBeginToLeaveOpening + "," + thresholdArriveEndgame);
			//System.err.println("[o,e] = [" + openingPercent + "," + endgamePercent + "]");			
		}
		else {			
			openingPercent = Math.min(100, Math.max(0, (100*(totalNonPawnValue-thresholdArriveEndgame)) / (thresholdBeginToLeaveOpening-thresholdArriveEndgame)));
			endgamePercent = 100 - openingPercent;		
			
			//System.err.println("MIX: Total nonpawn value=" +  totalNonPawnValue);
			//System.err.println("[o,e] = [" + openingPercent + "," + endgamePercent + "]"+ " thresholds are " + thresholdBeginToLeaveOpening + "," + thresholdArriveEndgame);
		}		
		
		//evaulate value of piece on square
		int pos = 0;	
		
		//rooks files and ranks !
		Color colors[] = {Color.WHITE, Color.BLACK};		
		for (Color c : colors) {
			GameState.PieceBoards heroBoards = b.getBoardsForColor(c);
			long fileBlockers = heroBoards.kings | heroBoards.pawns;
			long rankBlockers = heroBoards.kings | heroBoards.pawns | heroBoards.bishops | heroBoards.knights;
					
			for (MaskIterator I = new MaskIterator(heroBoards.rooks); I.hasNext();) {
				long thisRook = I.next();
				int thisRookSquare = maskToIndex(thisRook);				
				long fileRays = (getRayAttacks(RayDirection.N, thisRookSquare, fileBlockers) | getRayAttacks(RayDirection.S, thisRookSquare, fileBlockers)) & (~fileBlockers);
				
				int score = 0;// countBitsBinary(fileRays)*2;
	
				//consider mobility on this rank. we'll go with not blocked by friendly rooks or queens for this purpose (to encourage connection)
				long rankRays = (getRayAttacks(RayDirection.W, thisRookSquare, rankBlockers) | getRayAttacks(RayDirection.E, thisRookSquare, rankBlockers)) & (~rankBlockers);
				for (int x = 0; x < 8; x++) {
					if ((rankRays & fileFullMask[x]) != 0) {
						score += rookFileScore[x];
					}
				}			
				
				pos += score * c.sign();		
			}		
		}
				
		//Piece values by scores
		for (Piece p : Piece.values()) {
			for (MaskIterator I = new MaskIterator(b.white.getBoardForPiece(p)); I.hasNext();) {
				long thisPiece = I.next();
				pos += (pieceSquareScoreOpening[p.index][maskToIndex(thisPiece)] * openingPercent) / 100;
				pos += (pieceSquareScoreEndgame[p.index][maskToIndex(thisPiece)] * endgamePercent) / 100;
			}
	
			for (MaskIterator I = new MaskIterator(b.black.getBoardForPiece(p)); I.hasNext();) {
				long thisPiece = I.next();
				pos -= (pieceSquareScoreOpening[p.index][flipIndex(maskToIndex(thisPiece))] * openingPercent) / 100;
				pos -= (pieceSquareScoreEndgame[p.index][flipIndex(maskToIndex(thisPiece))] * endgamePercent) / 100;
			}
		}
		
		//DOUBLED PAWNS ARE BAD.
		for (int x = 0; x < 8; x++) {
			final int countWhiteExtraPawnsOnThisFile =	Math.max( countBitsBinary(b.white.pawns & fileFullMask[x])-1, 0);
			final int countBlackExtraPawnsOnThisFile =  Math.max( countBitsBinary(b.black.pawns & fileFullMask[x])-1, 0);
			final int doublePawnPenalty = -15;
			pos += countWhiteExtraPawnsOnThisFile * doublePawnPenalty;
			pos -= countBlackExtraPawnsOnThisFile * doublePawnPenalty;
		}

		if (b.white.kcastle || b.white.qcastle) {
			pos += 20;			
		}	
		if (b.black.kcastle || b.black.qcastle) {
			pos -= 20;
		}
		
		//king safety : give points for pawns that shiled the king
		final int blackKingSquare = maskToIndex(b.black.kings);
		final int blackSafety = 	15*((b.black.pawns & (blackPawnSinglePushes[blackKingSquare] | blackPawnDoublePushes[blackKingSquare]))  != 0 ? 1 : 0)
							+		5*((b.black.pawns & blackPawnDoublePushes[blackKingSquare]) != 0 ? 1 : 0)
							+		6*(countBitsBinary(b.black.pawns & blackPawnCaps[blackKingSquare]));
		final int whiteKingSquare = maskToIndex(b.white.kings);
		final int whiteSafety = 	15*((b.white.pawns & (whitePawnSinglePushes[whiteKingSquare] | whitePawnDoublePushes[whiteKingSquare])) != 0 ? 1 : 0)
							+		5*((b.white.pawns & whitePawnDoublePushes[whiteKingSquare]) != 0 ? 1 : 0)
							+		6*(countBitsBinary(b.white.pawns & whitePawnCaps[whiteKingSquare]));
	
		pos += whiteSafety-15;
		pos -= blackSafety-15;
		//not over so make estimate
		return new EvaluationResult(EnumOutcome.STILL_PLAYING, material+pos);
	}
	
	private final int INFINITY = 1000000;
	public ScoredMove getBestMoveWithIterativeDeepening(int timeLimitMilliseconds, int maxDepth)  {		
		stats = new SearchStatistics();
		
		int depth = 0;
		long startTime = System.currentTimeMillis();
		long elapsedTime = 0;

		ScoredMove bm = null;
		while ((elapsedTime < timeLimitMilliseconds) && (depth < maxDepth)) {
			depth += 1;
			
			bm = launchAlphaBetaSearch(depth);
			elapsedTime = (new Date()).getTime() - startTime;			
		}
		
		stats.maxDepth = depth;
		stats.elapsedTimeMS = elapsedTime;	
		//killerMoveSystem.dump();
		
		return bm;
	}	
	
	private ScoredMove launchAlphaBetaSearch(int depth) {
		killerMoveSystem = new KillerMovesTable(50);
		if (settings.inspectLine == null) {
			return getBestMoveAlphaBeta(0,depth, -INFINITY, +INFINITY, 1, false);
		} else {
			return getBestMoveAlphaBeta(0,depth, -INFINITY, +INFINITY, 1, true);
		}
	}
	
	
	public ScoredMove getBestMoveAlphaBeta(int depth) {		
		stats = new SearchStatistics();
			
		long startTime = System.currentTimeMillis();
		long elapsedTime = 0;

		ScoredMove bm = null;
		bm = launchAlphaBetaSearch(depth);
		if (bm.cutoff) {
			System.err.println("Warning: max window returned cutoff! [score=" + bm.score + "]");
		}
		elapsedTime = (new Date()).getTime() - startTime;			
		
		stats.maxDepth = depth;
		stats.elapsedTimeMS = elapsedTime;	
		
		return bm;
		
	}
	

	final class DirtyHeuristic implements Comparator<Move> {
		private int currentDepth;
		
		public DirtyHeuristic() {
			currentDepth = -1;
			hintMove = null;
		}
		public DirtyHeuristic(int _currentDepth) {
			currentDepth = _currentDepth;
			hintMove = null;
		}
		public DirtyHeuristic(Move _hintMove, int _currentDepth) {
			currentDepth = _currentDepth;
			hintMove = _hintMove;
		}
		
		private Move hintMove, killerMove;
		private int score(Move r) {
			int ret = 0;
			if (hintMove != null) {
				if (r.compare(hintMove)) {
					return 1000;
				}
			}
			final int loudMin = 100;
			if (r.isPromotion || r.isCapture) {
				ret += loudMin;
			}
			if (r.isPromotion)
			{
				ret += (r.promotionPiece.naiveValue-1)*10;
			}
			if (r.isCapture) {
				ret += (getPieceAtMask(r.getCaptureMask()).p.naiveValue*10 - getPieceAtMask(r.fromMask).p.naiveValue); 
			}
			if (settings.killerMove && (currentDepth >= 0)) {
				int killerRank = killerMoveSystem.getKillerMoveRank(currentDepth, r);
				if (killerRank > 0) { //0 means wasnt killer.
					ret += (loudMin/2)+ (killerMoveSystem.getmaxKillerMoveRank(currentDepth) - killerRank);
				}
			}
			if (r.isCheck) {
				ret += 1;
			}
			return ret;				
		}
		@Override
		public int compare(Move a, Move b) {
			return score(b)-score(a);
		}			
	}
	
	public enum NodeType {
		EXACT,
		LOWERBOUND,
		UPPERBOUND;
	}

	final class HashTable 
	{		
		final class HashEntry {
			long hashCode;
			ScoredMove bm;
			int depth;
			int vintage; //size of move history when this position was saved
			NodeType type;			
		}
		
		long zobristTableW[][];		
		long zobristTableB[][];
		HashEntry data[];
		
		long zobristWhiteToMove;
		long zobristBlackToMove;
		
		long zobristWhiteKCastle;
		long zobristWhiteQCastle;
		long zobristBlackKCastle;
		long zobristBlackQCastle;
		
		public long zobristDeltaForPieceAt(long atMask) {
			int sq = maskToIndex(atMask);
			ColoredPiece cp = getPieceAtMask(atMask);
			if (cp.c == Color.BLACK) {
				return zobristTableB[cp.p.index][sq];
			} 
			else if (cp.c == Color.WHITE) {
				return zobristTableW[cp.p.index][sq];
			}
			else {
				return 0L;
			}
		}
		
		HashTable() {
			//TODO: deal with the random seed here
			Random r = new Random(123456);
			zobristTableW = new long[6][64];
			zobristTableB = new long[6][64];
			for (int p = 0;p<6;p++) {
				for (int i = 0;i<64;i++) {
					zobristTableW[p][i] = r.nextLong();
					zobristTableB[p][i] = r.nextLong();
				}
			}
			
			zobristWhiteToMove = r.nextLong();
			zobristBlackToMove = r.nextLong();
			
			zobristWhiteKCastle = r.nextLong();
			zobristWhiteQCastle = r.nextLong();
			
			zobristBlackKCastle = r.nextLong();
			zobristBlackQCastle = r.nextLong();								
			
			//For now just using this for zobrist keys for repetition detection etc. Not for hash table.
			//So dont allocate t able.
			data = new HashEntry[HASH_SIZE];
		}			
		
		final int HASH_SIZE = 500000;
		
		long getKey() {
			long k = 0;
			for (Piece p : Piece.values()) {
				long whitep =  b.white.getBoardForPiece(p);
				long blackp =  b.black.getBoardForPiece(p);
				//white
				for (MaskIterator I = new MaskIterator(whitep); I.hasNext();){
					int square = maskToIndex(I.next());
					k ^= zobristTableW[p.index][square];
				}
				//black
				for (MaskIterator I = new MaskIterator(blackp); I.hasNext();){
					int square = maskToIndex(I.next());
					k ^= zobristTableB[p.index][square];
				}				
			}
			if (b.toPlay == Color.WHITE) {
				k ^= zobristWhiteToMove;
			} else {
				k ^= zobristBlackToMove;
			}
			
			if (b.white.kcastle) {
				k ^= zobristWhiteKCastle;
			}
			if (b.white.qcastle) {
				k ^= zobristWhiteQCastle;
			}
			if (b.black.kcastle) {
				k ^= zobristBlackKCastle;
			}
			if (b.black.qcastle) {
				k ^= zobristBlackQCastle;
			}
			
			return k;
		}
		
		int index(long k) {
			return (int)(((k ^ (k>>32))&(0xFFFFL)) % (long)HASH_SIZE);
		}
		
		HashEntry get(long k) {
			HashEntry ret = data[index(k)];
			if (ret != null) {				
				if (ret.hashCode == k) {
					return ret;
				}
			}
			return null;			
		}
		
		void set(long k, ScoredMove bm, int d, NodeType type) {
			data[index(k)] = null;
			HashEntry he = new HashEntry();
			he.type = type;
			he.vintage = moveHistoryList.size();
			he.hashCode = k;
			he.bm = new ScoredMove(bm);
			he.depth = d;
			data[index(k)] = he;
		}
	}
	
	private HashTable h;
		
	private ScoredMove getQuiescenceScore(int depth, int alpha, int beta, int checksRemaining, boolean inspect) {
		inspect = inspect && settings.inspectIntoQuiescence;
		if (depth != 0) {
			stats.quiescenceNodes += 1;
		}
		
		ScoredMove bm = new ScoredMove();	
		bm.depth = depth;
		//game over (checkmate or stalemate)
		
		
		LinkedList<Move> legalMoves = this.getLegalMoves();
		EvaluationResult immediate = this.evaluatePosition(legalMoves);

		String indent = "";
		for (int i = 0; i > depth-1; i--) {
			indent += "\t";
		}
		

		if (inspect) System.out.println(indent + "(Qui) Quiescence at [" + depth + "] [" + alpha + "," + beta + "] <imm=" + immediate.score + ">\n" + renderState(indent));
		
		/*//see if its a cutoff right away
		if (b.toPlay == Color.WHITE) {
			if (immediate.score >= beta) {
				bm.score = beta;
				bm.cutoff = true;
				return bm;
			}
		}
		else if (b.toPlay  == Color.BLACK) {
			if (immediate.score <= alpha) {					
				bm.score = alpha;
				bm.cutoff = true;
			}
		}	*/				
		
						
		//too deep so just return immediate value
		if (( immediate.outcome.isGameOver() )||(depth < -settings.maxQuiescencePly)) {
			bm.cutoff = false;
			bm.score = immediate.score;
			bm.move = null;
			bm.line = new LinkedList<Move>();
			//System.out.println(indent + "(Qui) hit depth limit returning " + bm.score);
			return bm;
		}
		
		//filter legal moves to captures
		LinkedList<Move> loudMoves = new LinkedList<Move>();
		for (Move m : legalMoves) {
			if (m.isCapture || (m.isCheck && (checksRemaining > 0)) || (m.isPromotion) ) {
				loudMoves.add(m);
			}
		}
		//sort move options according to heuristic
		Collections.sort(loudMoves, new DirtyHeuristic());
		
		bm.move = null;
		if (b.toPlay == Color.BLACK) {
			bm.score = beta;
		} else {
			bm.score = alpha;
		}
		
		bm.cutoff = true;

		for (Move m : loudMoves) {			
			if (alpha > beta) {
				if (inspect) System.err.println(indent + "!!!Bad window ["+alpha+","+beta+"]");
				bm.cutoff = true;
				return bm;
			}
			
			if (inspect) System.out.println(indent + "(Qui) Trying " + m.toString());			
			
			boolean prune = false;			

			int newChecksRemaining = checksRemaining;
			if (m.isCheck && (m.isCapture == false)) {
				newChecksRemaining = checksRemaining-1;
			}
			
			final int margin = settings.deltaPruneMargin;
			if (m.isCheck == false) { //never prune checks
				if (m.isCapture) {
					int nom = getPieceAtMask(m.getCaptureMask()).p.naiveValue*100;
					if (b.toPlay == Color.WHITE) { 
						if (immediate.score + nom + margin < alpha) {
							prune = true;
						}
					}
					else if (b.toPlay == Color.BLACK) { 
						if (immediate.score - (nom + margin) > beta) {
							prune = true;
						}
					}
					if(prune) {
						if (inspect) System.out.println(indent + "Pruned couldnt reach: [" + alpha + "," + beta + "] (from imm=" + immediate.score+ ")");
						continue;
					}
				}
			}			
			
			pushMove(m);			
			ScoredMove r = getQuiescenceScore(depth-1,alpha,beta,newChecksRemaining,inspect);
			popMove();
			//System.out.println(indent + "(qui) score was " + r.score + "cutoff=" + r.cutoff);

			if (b.toPlay == Color.WHITE) {
				if (r.score >= alpha) {
					alpha = r.score;
				}					
				if (r.score >= beta) {
					bm.score = beta;
					bm.cutoff = true;
					//System.out.println(indent + "cutoff score=" + bm.score);
					return bm;
				}	

				if (r.score > bm.score) {
					bm.score = r.score;

					if (r.cutoff == false) {
						bm.move = m;
						bm.line = r.line;
					}
				}
			}
			else if (b.toPlay == Color.BLACK) {
				if (r.score <= beta) {
					beta = r.score;
				}				
				if (r.score <= alpha) {					
					bm.score = alpha;
					bm.cutoff = true;
					//System.out.println(indent + "cutoff score=" + bm.score);
					return bm;
				}
				if (r.score < bm.score) {
					bm.score = r.score;

					if (r.cutoff == false) {
						bm.move = m;
						bm.line = r.line;
					}
				}
			}			
			if (bm.move != null) {
				//System.out.println(indent + "(Qui) best move is " + bm.move.toString() + " [score=" + bm.score + "]");
			} else {
				//System.out.println(indent + "(Qui)  best move is null ");
			}			
		}
		
		//if we got here:
		//try "stand pat"
		if (b.toPlay == Color.WHITE) {
			if (immediate.score >= alpha) {
				bm.score = immediate.score;
				bm.line = new LinkedList<Move>();
				bm.move = new Move(0,0);
				bm.standPat = true;
				bm.cutoff = false;
				//System.out.println("(Qui) stood pat score = " + immediate + "\n");
				return bm;
			}
		}
		else if (b.toPlay == Color.BLACK) {
			if (immediate.score <= beta) {
				bm.score = immediate.score;
				bm.line = new LinkedList<Move>();
				bm.move = new Move(0,0);
				bm.standPat = true;
				bm.cutoff = false;
				//System.out.println("(Qui) stood pat score = " + immediate + "\n");
				return bm;
			} 
		}		
			
		if (bm.move != null) {
			bm.line.addFirst(bm.move);
			bm.cutoff = false;
		}
		else
		{
			bm.cutoff = true;
		}
		return bm;
	}
	

	final class KillerMovesTable {
		public final int KILLERS_PER_PLY = 2;
		int maxDepth;
		private Move killerMove[][];
		private int killerCount[][];
		
		public int getmaxKillerMoveRank(int depth) {
			//returns the lowest rank that getKillerMOveRank could return 
			//ie if we have 4 killer moves max, then this is 4.
			return KILLERS_PER_PLY;
		}
		public int getKillerMoveRank(int depth, Move m) {
			//returns 0 if its not a killer move
			//otherwise 1 for the most popular
			//2 for the second most popular
			//etc.
			int foundIndex = -1;
			for (int i = 0; i < KILLERS_PER_PLY; i++) {
				if (killerMove[depth][i] != null) {
					if (m.compare( killerMove[depth][i] )) {
						if (foundIndex == -1) {
							foundIndex = i;
						} else {
							System.err.println("Warning: duplicate killer move");	
						}
					}
				}
			}
			if (foundIndex < 0) {
				return 0;
			} 
			else {
				int rank = 1;
				for (int i = 0; i < KILLERS_PER_PLY; i++) {
					if (killerMove[depth][i] != null) {
						if (killerCount[depth][i] > killerCount[depth][foundIndex]) {
							rank++;
						}
					}
				}
				return rank;
			}
		}
		public void considerKillerMove(Move m, int d) {
			//if quiet then save.
			if ((m.isCapture == false) && (m.isPromotion == false)) {
				for (int i = 0; i < KILLERS_PER_PLY; i++) {
					if (killerMove[d][i] != null) {
						if (m.compare( killerMove[d][i] )) { //found it here
							killerCount[d][i] += 1;
							return;
						}
					}
				}
				int leastPopularIndex = 0;
				//if we get here we didnt find it
				//so use it to replace least popular one (or default to index 0)
				for (int i = 0; i < KILLERS_PER_PLY; i++) {
					if (killerCount[d][i] <= killerCount[d][leastPopularIndex]) {
						leastPopularIndex = i;
					}
				}
				killerMove[d][leastPopularIndex] = new Move(m);
				killerCount[d][leastPopularIndex] = 1;
			}
		}
		
		public KillerMovesTable(int _maxDepth) {
			maxDepth = _maxDepth;
			this.killerCount = new int[maxDepth+1][KILLERS_PER_PLY];
			this.killerMove = new Move[maxDepth+1][KILLERS_PER_PLY];
		}		
		
		public void dump() {
			for (int d = 0; d <= maxDepth; d++) {
				System.out.println("Depth=" + d);
				for (int i = 0; i < KILLERS_PER_PLY; i++) {
					System.out.println(killerMove[d][i] + "\t" + killerCount[d][i]);
				}
			}
		}
	}	
	
	public boolean isNullMoveAppropriate() {
		//No null move allowed if we are in check
		if (isCheck(b.toPlay)) {
			return false;
		}
		//No null move allowed when we have one or less minor piece remaining (eg king and pawns)
		final long nonKingPawns = b.getBoardsForColor(b.toPlay).all ^ (b.getBoardsForColor(b.toPlay).pawns|b.getBoardsForColor(b.toPlay).kings); 
		if (countBitsBinary(nonKingPawns) <= 1) {
			return false;
		}
		//If we got here then go for it
		return true;
	}
	
	private KillerMovesTable killerMoveSystem;	
	private ScoredMove getBestMoveAlphaBeta(int depthIn, int depthLeft, int alpha, int beta, int nullMoveBudget, boolean inspect)  {
		boolean inspectHere = false;
		if (inspect) {
			if (depthIn == settings.inspectLine.length) {
				inspectHere = true;
			}
		}
		ScoredMove hashHintMove = null;
		if (settings.hashing && (depthLeft >= settings.minHashDepth)) {
			HashTable.HashEntry lookupAttempt = h.get(b.zobrist);
			//System.out.println(lookupAttempt);
			if (lookupAttempt != null) {
				//adjust checkmate for vintage
				int adj = 0; 
				if (lookupAttempt.bm.score > ScoredMove.GG) {
					adj = lookupAttempt.vintage - moveHistoryList.size(); 
				} else if (lookupAttempt.bm.score < -ScoredMove.GG){
					adj = -(lookupAttempt.vintage - moveHistoryList.size());
				}
				//System.out.println(lookupAttempt.depth + " vs cd=" + depth);
				if (lookupAttempt.depth >= depthLeft) {
					if (lookupAttempt.type == NodeType.EXACT) {
						//System.out.println("Hash hit " + lookupAttempt.bm.move.toString());
						stats.hashHits += 1;
						ScoredMove ret = new ScoredMove(lookupAttempt.bm);
						ret.score += adj;
						return ret;
					}
				} else { //hash from a previous search and lower depth. use it as a hint
					if (lookupAttempt.bm != null) {
						stats.hashHints += 1;
						hashHintMove = lookupAttempt.bm;
					}
				}
			}		
		}		
		stats.alphaBetaNodes += 1;
		
		ScoredMove bm = new ScoredMove();		
		//horizon
		if (depthLeft <= 0) {
			if (settings.enableQuiescence) {
				bm = this.getQuiescenceScore(0, alpha, beta, settings.maxQuiescenceChecks, inspect);				
				
				bm.move = null;
				bm.line = new LinkedList<Move>();
				return bm;
			}
			else {
				bm.score = this.evaluatePosition().score;
				bm.move = null;
				bm.line = new LinkedList<Move>();
				bm.cutoff = false;

				return bm;					
			}
		}		

		LinkedList<Move> legalMoves = this.getLegalMoves();
		//game over (checkmate or stalemate)
		EvaluationResult immediate = this.evaluatePosition(legalMoves);
		if (immediate.outcome.isGameOver()) {
			bm.cutoff = false;
			bm.score = immediate.score;
			bm.move = null;
			bm.line = new LinkedList<Move>();
			return bm;					
		}
		
		if (legalMoves.size() == 1 && depthIn < 20) //Single reply extension 
			depthLeft += 1;
		
		String indent = "";
		for (int i = 0; i < depthIn; i++) {
			indent += "\t";
		}
				
		//System.out.println(indent + "AB at [" + depth + "] [" + alpha + "," + beta + "]\n" + renderState(indent));
		
		//Try the nullmove.
		if (settings.enableNullMoveAB) {
			if (isNullMoveAppropriate() && (nullMoveBudget>0)) {
				if (inspect) System.out.println("EXAMINING NULLMOVE...");
				
				pushNullMove();
				//search to 2 ply less
					ScoredMove r = getBestMoveAlphaBeta(depthIn+1, depthLeft-3, alpha, beta, nullMoveBudget-1, inspect);
				popMove();
				/*if (r.cutoff == true) {
					System.out.println(indent + "Null move gave cutoff on ");
					System.out.println("[" + alpha + "," + beta + "] (score=" + r.score);
					System.out.println(this.renderState(indent));
				}*/
				if (b.toPlay == Color.WHITE) {
					if (r.score >= beta) {
						bm.score = beta;
						bm.cutoff = true;
						stats.nullMoveCutoffs += 1;
						return bm;
					}
				} else {
					if (r.score <= alpha) {
						bm.score = alpha;
						bm.cutoff = true;
						stats.nullMoveCutoffs += 1;
						return bm;						
					}
				}
				if (inspect) System.out.println("NO CUTOFF FROM NULL MOVE");
			}			
		}
		
		if (settings.killerMove) {
			Collections.sort(legalMoves, new DirtyHeuristic(depthIn));
		} else {
			Collections.sort(legalMoves, new DirtyHeuristic(-1));
		}
	
		bm.move = null;
		if (b.toPlay == Color.BLACK) {
			bm.score = beta;
		} else {
			bm.score = alpha;
		}
		
		bm.cutoff = true;

		for (Move m : legalMoves) {			
			//System.out.println(indent + "Trying " + m.toString());
			
			boolean inspectFurther = false;
			if (inspect) {
				if (depthIn < settings.inspectLine.length) {
					//System.err.println(m.toString() + "' vs '" + settings.inspectLine[depthIn]);
					if (m.toString().compareTo(settings.inspectLine[depthIn]) == 0) {
						inspectFurther = true;
						//System.err.println("inspecting further");
					}
				}
			}
			
			if (inspectHere) {
				System.out.println( "INSPECTING: Considering '" + m.toString() + "' on ");
				System.out.println( "window = [" + alpha + "," + beta + "]");
				System.out.println( this.renderState("") );
			}
			
			pushMove(m);			
			ScoredMove r = getBestMoveAlphaBeta(depthIn+1,depthLeft-1,alpha,beta,nullMoveBudget,inspectFurther);
			if (r.cutoff == false) {
				saveHash(r, depthLeft-1, NodeType.EXACT);
			}
			popMove();
			
			if (inspectHere) {
				System.out.println(indent + "score was " + r.score + "cutoff=" + r.cutoff);
				if (r.line != null) {
					System.out.println(" via " + r.line.toString());
				}
			}

			if (b.toPlay == Color.WHITE) {
				if (r.score >= alpha) {
					alpha = r.score;
				}					
				if (r.score >= beta) {
					bm.score = beta;
					bm.cutoff = true;
					killerMoveSystem.considerKillerMove(m,depthIn);
					return bm;
				}	

				if (bm.move == null || r.score > bm.score) {
					bm.score = r.score;

					if (r.cutoff == false) {
						bm.move = m;
						bm.line = r.line;
					}
				}
			}
			else if (b.toPlay == Color.BLACK) {
				if (r.score <= beta) {
					beta = r.score;
				}				
				if (r.score <= alpha) {					
					bm.score = alpha;
					bm.cutoff = true;
					killerMoveSystem.considerKillerMove(m,depthIn);
					return bm;
				}
				if (bm.move == null || r.score < bm.score) {
					bm.score = r.score;

					if (r.cutoff == false) {
						bm.move = m;
						bm.line = r.line;
					}
				}
			}			
			if (bm.move != null) {
				//System.out.println(indent + " best move is " + bm.move.toString() + " [score=" + bm.score + "]");
			} else {
				//System.out.println(indent + " best move is null ");
			}
		}
		//String s = this.moveHistory.toString();
		//System.out.println(String.format("%s , Chose %d with score %d", s, bm.move, bm.score ) );
		bm.depth = depthLeft;
		
		if (bm.move != null) {
			bm.line.addFirst(bm.move);
			bm.cutoff = false;
			/*
			if (settings.hashing && depth >= settings.minHashDepth) {
				HashableBoard h = new HashableBoard(this);			
				transpositionTable.put(h, new ScoredMove(bm));
			}*/
		}
		else
		{
			bm.cutoff = true;
		}
		return bm;
	}

	private void pushNullMove() {
		MoveHistoryNode mh = new MoveHistoryNode();
		mh.stateBefore = new GameState(b);
		mh.m = new Move(0,0);
		mh.m.nullMove = true;
		moveHistoryList.addFirst(mh);
		
		// en passant can only be done immediately after the double move. so we set to 0 here to disable it 
		this.b.enPassantTarget = 0;
		
		b.toPlay = b.toPlay.Enemy();
		b.zobrist |= h.zobristWhiteToMove | h.zobristBlackToMove;		
	}

	private void saveHash(ScoredMove bm, int depth, NodeType type) {
		if (settings.hashing) {
			h.set(b.zobrist, bm, depth, type);
		}		
	}
	
	public boolean makeMoveByString(String s) {
		LinkedList<Move> legal = this.getLegalMoves();
		for (Move m : legal) {
			//System.err.println("'" + m.toString() + "' vs '" + s + "'" + "[" + m.toString().compareTo(s) + "]");
			if (m.toString().compareTo(s) == 0) {
				this.pushMove(m);
				return true;
			}
		}
		return false;
	}

	public LinkedList<Move> getMoveHistory() {
		LinkedList<Move> r = new LinkedList<Move>();
		for (Iterator<MoveHistoryNode> I = moveHistoryList.descendingIterator(); I.hasNext();) {
			r.add(I.next().m);			
		}
		return r;
	}	
	
	public long getRecalculatedZobristKey() {
		return h.getKey();
	}
	
	public long getIncrementalZobristKey() {
		return b.getZobrist();
	}
}
