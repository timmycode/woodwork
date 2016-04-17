package application;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedList;
import java.util.Random;
import java.util.Iterator;
import java.util.LinkedList;

import application.Board.Color;
import application.Board.ScoredMove;
import application.Board.SearchStatistics;

public class Board {
	public static boolean isDarkSquare(int x , int y) {
		return ( (((x+y) % 2) == 0) ? true : false); 
	}
	private class GameState {
		public GameState(GameState r) {
			super();
			this.white = new PieceBoards(r.white);
			this.black = new PieceBoards(r.black);
			this.toPlay = r.toPlay;
		}
		
		public GameState() {
			this.white = new PieceBoards();
			this.black = new PieceBoards();
			this.toPlay = Color.WHITE;
		}

		
		private class PieceBoards {
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
		
		PieceBoards white, black;
		Color toPlay;		
		
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
	};
	
	GameState b;
	
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
	
	private long[] whitePawnCaps;
	private long[] blackPawnCaps;
	private long[] whitePawnCapsReverse;
	private long[] blackPawnCapsReverse;
	private long whitePromotionSquares;
	private long blackPromotionSquares;
		
	private long[][] rayAttacks; // [i][j] = bit mask of ray from square j in direction i
	
	//scoring squares
	private int pieceSquareMobility[][];
	private int squareScore[];
	private int pieceSquareScore[][];
	
	private int whitePawnSquareScore[];
	private int blackPawnSquareScore[];
	
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
	
	public static String squareName(int index) {
		int y = index/8;
		int x = index%8;
		return "" + "abcdefgh".charAt(x) + (y+1);
	}
	
	public static int slowMaskToIndex(long m) {
		for (int i = 0; i < 64; i++) {
			if ((m & (1L<<i))!=0) {
				return i;
			}
		}	
		return -1;
	}
	
	
	public enum Color {
		WHITE, BLACK, EMPTY;
		public Color Enemy() {
			if (this == Color.WHITE) {
				return Color.BLACK;
			} else if (this == Color.BLACK) {
				return Color.WHITE;
			} else {
				return null;
			}
		}
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
	
	public class ColoredPiece {
		Piece p;
		Color c;
	}	
	
	public ColoredPiece getPieceAt(int x, int y) {
		long mask = 1L << index(x,y);
		return getPieceAt(mask);
	}
		
	public ColoredPiece getPieceAt(long mask) {
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
	
	public boolean isWithinBoard(int x, int y) {
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
	
	class MoveHistoryNode {
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
						
		
		if (m.isCastle == true) {
			//For castle moves, we know the target squares are empty anyway. no need to clear them
			
			//copy king to new square
			b.copyPieceAllBoards(m.cm.KingFrom, m.cm.KingTo);
			//clear old king square
			b.doAndWithAllBoards(~m.cm.KingFrom);
			
			//copy rook to new square
			b.copyPieceAllBoards(m.cm.RookFrom, m.cm.RookTo);
			//clear old rook square
			b.doAndWithAllBoards(~(m.cm.RookFrom)); 			
		}
		else if (m.isPromotion == true) {
			//clear target square
			b.doAndWithAllBoards(~m.toMask);						
			//create new piece at target square
			setPieceAt(m.toMask, m.promotionPiece, b.toPlay);
			//clear from square 
			b.doAndWithAllBoards(~m.fromMask);
		} else {
			//clear target square
			b.doAndWithAllBoards(~m.toMask);						
			//copy from old square
			b.copyPieceAllBoards(m.fromMask, m.toMask);
			//clear from square 
			b.doAndWithAllBoards(~m.fromMask);			
		}
		
		//Check castling rights are intact		
		
		if ( ((CastleMovePattern.WHITE_KING_SIDE.KingFrom & b.white.kings) == 0) || ((CastleMovePattern.WHITE_KING_SIDE.RookFrom & b.white.rooks) == 0) ) {
			b.white.kcastle = false;
		}		
		if ( ((CastleMovePattern.WHITE_QUEEN_SIDE.KingFrom & b.white.kings) == 0) || ((CastleMovePattern.WHITE_QUEEN_SIDE.RookFrom & b.white.rooks) == 0) ) {
			b.white.qcastle = false;
		}		
		if ( ((CastleMovePattern.BLACK_KING_SIDE.KingFrom & b.black.kings) == 0) || ((CastleMovePattern.BLACK_KING_SIDE.RookFrom & b.black.rooks) == 0) ) {
			b.black.kcastle = false;
		}		
		if ( ((CastleMovePattern.BLACK_QUEEN_SIDE.KingFrom & b.black.kings) == 0) || ((CastleMovePattern.BLACK_QUEEN_SIDE.RookFrom & b.black.rooks) == 0) ) {
			b.black.qcastle = false;
		}		
		
		//System.err.println("After");
		//System.err.println(renderBitBoard(b.white.queens));
		
		//next player's move
		b.toPlay = b.toPlay.Enemy();
	}
	
	MoveHistoryNode popMove() {
		MoveHistoryNode mh = moveHistoryList.pop();
		b = mh.stateBefore;
		return mh;
	}
	
	private void attemptAddMoveToList(LinkedList<Move> theList, Move theMove) {
		if (theMove != null) {
			//must be sure it wouldnt put us in check
			
			pushMove(theMove);
			if ( isCheck( b.toPlay.Enemy() ) == false ) {
				theList.add(theMove);
			}
			popMove();	
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
		
		pieceSquareScore = new int[6][64];
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
		
		//pre-computed knight moves
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
				pieceSquareScore[Piece.KNIGHT.index][index(x,y)] = attackSq/3;	//sum of centrality-value of attacked squares
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
			pieceSquareScore[Piece.KING.index][index(x,y)] = -squareScore[index(x,y)]*3;	//far away from centre as possible
			}
		}
			
		//pre-computed pawn pushes from each square
		whitePawnSinglePushes = new long[64];
		whitePawnDoublePushes = new long[64];
		
		blackPawnSinglePushes = new long[64];
		blackPawnDoublePushes = new long[64];
		
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
				pieceSquareScore[Piece.BISHOP.index][index(x,y)] = total / 4; 
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
				pieceSquareScore[Piece.ROOK.index][index(x,y)] = (total / 4 - squareScore[index(x,y)]); 
			}
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
				pieceSquareScore[Piece.QUEEN.index][index(x,y)] = (total / 4 - squareScore[index(x,y)]*2) / 2;
			}
		}		
		
		whitePawnSquareScore = new int[64];
		blackPawnSquareScore = new int[64];

		//pawn square scores
		for (int x = 0; x < 8; x++) {
			for (int y = 0; y < 8; y++) {
				int rankScores[] = {0,10,10,10,20,40,200,1000};
				whitePawnSquareScore[index(x,y)] = rankScores[y];
				blackPawnSquareScore[index(x,y)] = rankScores[7-y];				 
			}
		}		
		
	
		/*
		for (Piece p : Piece.values()) {
			System.out.println(p.name() + " square scores:\n");
			System.out.println(renderArray(pieceSquareScore[p.index]));
		}*/
		
		//add the pieces
		loadFromFEN(fen);	
		System.out.println("Board loaded. Hash = " + h.getKey());
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
	
	
	public void loadFromFEN(String v) {
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
	
	public LinkedList<Move> getLegalMoves() {
		
		GameState.PieceBoards heroBoards = b.getBoardsForColor(b.toPlay);
		GameState.PieceBoards enemyBoards = b.getBoardsForColor(b.toPlay.Enemy());
		
		boolean isHeroInCheck = isCheck(b.toPlay);
		
		LinkedList<Move> moves = new LinkedList<Move>();
		
		Piece promotionPieces[] = { Piece.QUEEN, Piece.KNIGHT, Piece.ROOK, Piece.BISHOP  };
				
		//pawn moves
		for (MaskIterator pawn = new MaskIterator(heroBoards.pawns); pawn.hasNext();) {
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
									attemptAddMoveToList(moves, m);
								}							
							}
							else	//single push doesnt care about that
							{
								attemptAddMoveToList(moves, m);
							}
						} else { //promotion square so add possible promotions
							for (Piece p : promotionPieces) {
								Move a = attemptMakeMove(thisPawn, target);
								a.isPromotion = true;
								a.promotionPiece = p;
								attemptAddMoveToList(moves, a);
							}							
						}
					}
				}
			}
		}
		
		//knight moves
		for (MaskIterator knight = new MaskIterator(heroBoards.knights); knight.hasNext();) {
			long thisKnight = knight.next();			
			long thisKnightMoves = knightAttacks[ maskToIndex(thisKnight) ];
			for (MaskIterator I = new MaskIterator(thisKnightMoves); I.hasNext();) {
				Move m = attemptMakeMove(thisKnight, I.next());
				attemptAddMoveToList(moves, m);
			}
		}
		//rook moves
		for (MaskIterator rook = new MaskIterator(heroBoards.rooks); rook.hasNext();) {
			long thisRook = rook.next();			
			long thisRookMoves = getOrthogonalAttacks(maskToIndex(thisRook), b.white.all|b.black.all);
			for (MaskIterator I = new MaskIterator(thisRookMoves); I.hasNext();) {
				Move m = attemptMakeMove(thisRook, I.next());
				attemptAddMoveToList(moves, m);
			}			
		}
		//bishop moves
		for (MaskIterator bishop = new MaskIterator(heroBoards.bishops); bishop.hasNext();) {
			long thisBishop = bishop.next();
			//System.out.println("bishop from" + Board.squareName(maskToIndex(thisBishop)));
			//System.err.println(renderBitBoard(b.white.all|b.black.all));
			long thisBishopMoves = getDiagonalAttacks(maskToIndex(thisBishop), b.white.all|b.black.all);
		
			for (MaskIterator I = new MaskIterator(thisBishopMoves); I.hasNext();) {
				Move m = attemptMakeMove(thisBishop, I.next());
				attemptAddMoveToList(moves, m);
			}			
		}		
		//queen moves
		for (MaskIterator queen = new MaskIterator(heroBoards.queens); queen.hasNext();) {
			long thisQueen = queen.next();	
			int thisQueenIndex = maskToIndex(thisQueen);
			
			//System.out.println("queen from" + Board.squareName(maskToIndex(thisQueen)));
			//System.err.println("E:\n" + renderBitBoard(getRayAttacks(RayDirection.E,maskToIndex(thisQueen), b.white.all|b.black.all)));
			
			long thisQueenMoves = getOrthogonalAttacks(thisQueenIndex, b.white.all|b.black.all) | getDiagonalAttacks(thisQueenIndex, b.white.all|b.black.all);
			//System.err.println("M:\n" + renderBitBoard(thisQueenMoves));
			
			for (MaskIterator I = new MaskIterator(thisQueenMoves); I.hasNext();) {
				Move m = attemptMakeMove(thisQueen, I.next());
				attemptAddMoveToList(moves, m);
			}			
		}			
				
		//king moves
		for (MaskIterator king = new MaskIterator(heroBoards.kings); king.hasNext();) {
			long thisKing = king.next();	
			long thisKingMoves = kingAttacks[maskToIndex(thisKing)];

			//castling moves
			if (isHeroInCheck == false) {
				if ((thisKing & b.white.kings) != 0) { //is a white king
					if (b.white.kcastle) { //is white allowed castle kingside?	
						attemptAddMoveToList(moves, attemptConstructCastleMove(CastleMovePattern.WHITE_KING_SIDE));
					}
					if (b.white.qcastle) { 	
						attemptAddMoveToList(moves, attemptConstructCastleMove(CastleMovePattern.WHITE_QUEEN_SIDE));
					}				
				}
				else if ((thisKing & b.black.kings) != 0) { //is a black king
					if (b.black.kcastle) { 	
						attemptAddMoveToList(moves, attemptConstructCastleMove(CastleMovePattern.BLACK_KING_SIDE));
					}
					if (b.black.qcastle) { 	
						attemptAddMoveToList(moves, attemptConstructCastleMove(CastleMovePattern.BLACK_QUEEN_SIDE));
					}				
				}
			}
			
			for (MaskIterator I = new MaskIterator(thisKingMoves); I.hasNext();) {
				Move m = attemptMakeMove(thisKing, I.next());
				attemptAddMoveToList(moves, m);
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
	
	class SearchSettings {
		boolean hashing = false;
		int maxQuiescenceChecks = 0;
		public int minHashDepth;
		public boolean enableQuiescence = true;
	}
	
	class ScoredMove { 
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
			super();
			this.move = o.move;
			this.score = o.score;
			this.depth = o.depth;
			this.cutoff = o.cutoff;
			this.standPat = o.standPat;
			
			this.line = new LinkedList<Move>();
			for (Move m : o.line) {
				this.line.add(m);
			}
		}
		
		
	}	
	
	public SearchSettings settings;
	
	class SearchStatistics {
		public int maxDepth;
		public long elapsedTimeMS;
		public int evaluated;		
		
		public int quiescenceNodes = 0;
		public int alphaBetaNodes = 0;
		public int hashHits;
		public int hashHints;
	}
	
	public SearchStatistics stats;
	
	int evaluatePosition() {
		stats.evaluated += 1;
		LinkedList<Move> legalMoves = this.getLegalMoves();
		if (legalMoves.size() == 0) {
			if (isCheck(b.toPlay)) { 	//checkmate
				if (b.toPlay == Color.BLACK) {
					return ScoredMove.GG + (1000 - moveHistoryList.size());
				}
				else {
					return -(ScoredMove.GG + (1000 - moveHistoryList.size()));
				}
			} else {
				return 0;				//stalemate
			}
		}
		
		//material considerations
		int material = 0;
		for (Piece p : Piece.values()) {
			material += countBitsBinary(b.white.getBoardForPiece(p)) * p.naiveValue * 100;
			material -= countBitsBinary(b.black.getBoardForPiece(p)) * p.naiveValue * 100;
		}
		
		//evaulate value of piece on square
		int pos = 0;
		
		//rooks on open files!
		for (MaskIterator I = new MaskIterator(b.white.rooks); I.hasNext();) {
			long thisRook = I.next();
			int thisRookSquare = maskToIndex(thisRook);
			long rays = getRayAttacks(RayDirection.N, thisRookSquare, b.white.pawns|b.white.kings) | getRayAttacks(RayDirection.S, thisRookSquare, b.white.pawns|b.white.kings);
			int score = countBitsBinary(rays)*4;
			pos += score;			
		}		
		for (MaskIterator I = new MaskIterator(b.black.rooks); I.hasNext();) {
			long thisRook = I.next();
			int thisRookSquare = maskToIndex(thisRook);
			long rays = getRayAttacks(RayDirection.N, thisRookSquare, b.black.pawns|b.black.kings) | getRayAttacks(RayDirection.S, thisRookSquare, b.black.pawns|b.black.kings);
			int score = countBitsBinary(rays)*4;
			pos -= score;			
		}		
		
		for (Piece p : Piece.values()) {
			if (p != Piece.PAWN) { 
				for (MaskIterator I = new MaskIterator(b.white.getBoardForPiece(p)); I.hasNext();) {
					long thisPiece = I.next();
					pos += pieceSquareScore[p.index][maskToIndex(thisPiece)];
				}
		
				for (MaskIterator I = new MaskIterator(b.black.getBoardForPiece(p)); I.hasNext();) {
					long thisPiece = I.next();
					pos -= pieceSquareScore[p.index][maskToIndex(thisPiece)];
				}
			} else {
				for (MaskIterator I = new MaskIterator(b.white.getBoardForPiece(p)); I.hasNext();) {
					long thisPiece = I.next();
					pos += whitePawnSquareScore[maskToIndex(thisPiece)];
				}
		
				for (MaskIterator I = new MaskIterator(b.black.getBoardForPiece(p)); I.hasNext();) {
					long thisPiece = I.next();
					pos += blackPawnSquareScore[maskToIndex(thisPiece)];
				}
			}
		}
		//for the pawns
		
		
		//not over so make estimate
		return material+pos;
	}
	private final int INFINITY = 1000000;
	public ScoredMove getBestMoveWithIterativeDeepening(int timeLimitMilliseconds, int maxDepth)  {		
		stats = new SearchStatistics();
		
		int depth = 0;
		long startTime = System.currentTimeMillis();
		long elapsedTime = 0;

		ScoredMove bm = null;
		while (elapsedTime < timeLimitMilliseconds && depth < maxDepth) {
			depth += 1;
			bm = getBestMoveAlphaBeta(depth, -INFINITY, +INFINITY);
			elapsedTime = (new Date()).getTime() - startTime;			
		}
		
		stats.maxDepth = depth;
		stats.elapsedTimeMS = elapsedTime;	
		
		return bm;
	}	
	
	
	public ScoredMove getBestMoveAlphaBeta(int depth) {		
		stats = new SearchStatistics();
	
		long startTime = System.currentTimeMillis();
		long elapsedTime = 0;

		ScoredMove bm = null;
		bm = getBestMoveAlphaBeta(depth, -INFINITY, +INFINITY);
		if (bm.cutoff) {
			System.err.println("Warning: max window returned cutoff! [score=" + bm.score + "]");
		}
		elapsedTime = (new Date()).getTime() - startTime;			
		
		stats.maxDepth = depth;
		stats.elapsedTimeMS = elapsedTime;	
		
		return bm;
		
	}
	

	class DirtyHeuristic implements Comparator<Move> {
		public DirtyHeuristic() {
			hintMove = null;
		}
		public DirtyHeuristic(Move _hintMove) {
			hintMove = _hintMove;
		}
		
		Move hintMove;
		private int score(Move r) {
			int ret = 0;
			if (hintMove != null) {
				if (r.compare(hintMove)) {
					return 1000;
				}
			}
			if (r.isPromotion)
			{
				ret += r.promotionPiece.naiveValue;
			}
			if (r.isCapture) {
				ret += 10 + (getPieceAt(r.toMask).p.naiveValue*10 - getPieceAt(r.fromMask).p.naiveValue); 
			}
			if (r.isCheck) {
				ret += 2;
			}
			return ret;				
		}
		@Override
		public int compare(Move a, Move b) {
			return score(b)-score(a);
		}			
	}	
	

	class HashTable 
	{
		class HashEntry {
			long hashCode;
			ScoredMove bm;
			int depth;
		}
		
		long zobristTableW[][];		
		long zobristTableB[][];
		HashEntry data[];
		
		HashTable() {
			Random r = new Random(123456);
			zobristTableW = new long[6][64];
			zobristTableB = new long[6][64];
			for (int p = 0;p<6;p++) {
				for (int i = 0;i<64;i++) {
					zobristTableW[p][i] = r.nextLong();
					zobristTableB[p][i] = r.nextLong();
				}
			}
			
			data = new HashEntry[HASH_SIZE];
		}			
		
		final int HASH_SIZE = 1000000;
		
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
		
		void set(long k, ScoredMove bm, int d) {
			HashEntry he = new HashEntry();
			he.hashCode = k;
			he.bm = bm;
			he.depth = d;
			data[index(k)] = he;
		}
	}
	
	private HashTable h;
		
	private ScoredMove getQuiescenceScore(int depth, int alpha, int beta, int checksRemaining) {
		stats.quiescenceNodes += 1;
		
		ScoredMove bm = new ScoredMove();	
		bm.depth = depth;
		//game over (checkmate or stalemate)
		
		int immediate = this.evaluatePosition();
		
		if (b.toPlay == Color.WHITE) {
			if (immediate >= beta) {
				bm.score = beta;
				bm.cutoff = true;
				return bm;
			}
		}
		else if (b.toPlay  == Color.BLACK) {
			if (immediate <= alpha) {					
				bm.score = alpha;
				bm.cutoff = true;
			}
		}					
		
				
		String indent = "";
		for (int i = 0; i > depth-1; i--) {
			indent += "\t";
		}
		//System.out.println(indent + "(Qui) Quiescence at [" + depth + "] [" + alpha + "," + beta + "] <imm=" + immediate + ">\n" + renderState(indent));
		
		LinkedList<Move> legalMoves = this.getLegalMoves();
		if (legalMoves.size() == 0 || depth < -5) {
			bm.cutoff = false;
			bm.score = immediate;
			bm.move = null;
			bm.line = new LinkedList<Move>();
			//System.out.println(indent + "hit depth limit returning " + bm.score);
			return bm;
		}
		
		//filter legal moves to captures
		LinkedList<Move> loudMoves = new LinkedList<Move>();
		for (Move m : legalMoves) {
			if (m.isCapture || (m.isCheck && (checksRemaining > 0))) {
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
				System.err.println(indent + "!!!Bad window ["+alpha+","+beta+"]");
				bm.cutoff = true;
				return bm;
			}
			
			//System.out.println(indent + "(Qui) Trying " + m.toString());			
			
			boolean prune = false;			

			int newChecksRemaining = checksRemaining;
			if (m.isCheck && (m.isCapture == false)) {
				newChecksRemaining = checksRemaining-1;
			}
			
			final int margin = 300;
			if (m.isCapture) {
				int nom = getPieceAt(m.toMask).p.naiveValue*100;
				if (b.toPlay == Color.WHITE) { 
					if (nom + margin + immediate < alpha) {
						prune = true;
					}
				}
				else if (b.toPlay == Color.BLACK) { 
					if (immediate - nom - margin > beta) {
						prune = true;
					}
				}
				if(prune) {
					//System.out.println(indent + "(Qui) pruned this one. : " + alpha + "," + beta + " with imm=" + immediate + " nom = " + nom);
					continue;
				}
								
			}			
			
			pushMove(m);
			
			ScoredMove r = getQuiescenceScore(depth-1,alpha,beta,newChecksRemaining);
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
			if (immediate >= alpha) {
				bm.score = immediate;
				bm.line = new LinkedList<Move>();
				bm.move = new Move(0,0);
				bm.standPat = true;
				bm.cutoff = false;
				//System.out.println("(Qui) stood pat score = " + immediate + "\n");
				return bm;
			}
		}
		else if (b.toPlay == Color.BLACK) {
			if (immediate <= beta) {
				bm.score = immediate;
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
	
	private ScoredMove getBestMoveAlphaBeta(int depth, int alpha, int beta)  {			
		
		ScoredMove hashHintMove = null;
		if (settings.hashing && (depth >= settings.minHashDepth)) {
			HashTable.HashEntry lookupAttempt = h.get(h.getKey());
			//System.out.println(lookupAttempt);
			if (lookupAttempt != null) {
				//System.out.println(lookupAttempt.depth + " vs cd=" + depth);
				if (lookupAttempt.depth >= depth) {
					if (lookupAttempt.bm.cutoff == false) {
						//System.out.println("Hash hit " + lookupAttempt.bm.move.toString());
						return new ScoredMove(lookupAttempt.bm);
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
		if (depth <= 0) {
			if (settings.enableQuiescence) {
				bm = this.getQuiescenceScore(depth, alpha, beta, settings.maxQuiescenceChecks);				
				
				bm.move = null;
				bm.line = new LinkedList<Move>();
				saveHash(bm, depth);
				return bm;
			}
			else {
				bm.score = this.evaluatePosition();
				bm.move = null;
				bm.line = new LinkedList<Move>();
				bm.cutoff = false;
				saveHash(bm, depth);
				return bm;					
			}
		}		
			
		//game over (checkmate or stalemate)
		LinkedList<Move> legalMoves = this.getLegalMoves();
		if (legalMoves.size() == 0) {
			bm.score = this.evaluatePosition();
			bm.move = null;
			bm.line = new LinkedList<Move>();
			saveHash(bm, depth);
			return bm;					
		}				
		
		String indent = "";
		//System.out.println(indent + "AB at [" + depth + "] [" + alpha + "," + beta + "]\n" + renderState(indent));
	
	
		if (hashHintMove != null) {
			Collections.sort(legalMoves, new DirtyHeuristic(hashHintMove.move));
		}
		else {
			Collections.sort(legalMoves, new DirtyHeuristic());
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
			pushMove(m);						
			ScoredMove r = getBestMoveAlphaBeta(depth-1,alpha,beta);
			popMove();
			//System.out.println(indent + "score was " + r.score + "cutoff=" + r.cutoff);

			if (b.toPlay == Color.WHITE) {
				if (r.score >= alpha) {
					alpha = r.score;
				}					
				if (r.score >= beta) {
					bm.score = beta;
					bm.cutoff = true;
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
		bm.depth = depth;
		
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
		if (bm.cutoff == false) {
			saveHash(bm, depth);
		}
		return bm;
	}

	private void saveHash(ScoredMove bm, int depth) {
		if (settings.hashing) {
			h.set(h.getKey(), bm, depth);
		}		
	}

	public LinkedList<Move> getMoveHistory() {
		LinkedList<Move> r = new LinkedList<Move>();
		for (Iterator<MoveHistoryNode> I = moveHistoryList.descendingIterator(); I.hasNext();) {
			r.add(I.next().m);			
		}
		return r;
	}	
}
