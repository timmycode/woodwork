package application;

public enum CastleMovePattern {
	WHITE_KING_SIDE 	( 4,0,7,0 ),
	WHITE_QUEEN_SIDE	( 4,0,0,0 ),
	BLACK_KING_SIDE		( 4,7,7,7 ),
	BLACK_QUEEN_SIDE	( 4,7,0,7 );
		
	private CastleMovePattern( int kingx, int kingy, int rookx, int rooky ) {
		this.KingFrom = 1L << Board.index(kingx, kingy);
		this.RookFrom = 1L << Board.index(rookx, rooky);
		
		int d = 0;		
		if (rookx > kingx) {
			d = 1;
		} else {
			d = -1;
		}
		
		//the king moves 2 squares towards rook
		this.KingTraverse = 0;
		for (int t = 1; t < 2; t++) {
			this.KingTraverse |= 1L << Board.index(kingx + t*d, kingy);
		}
		
		this.KingTo = 1L << Board.index(kingx + 2*d, kingy);
		
		//the rook moves next to king
		this.RookTo = 1L << Board.index (kingx + d, kingy);		
		
		/*System.out.println("Castle move");
		System.out.println(Board.renderBitBoard(this.KingFrom));
		System.out.println(Board.renderBitBoard(this.KingTo));
		System.out.println(Board.renderBitBoard(this.KingTraverse));	*/	
		
		
	}
	
	public long KingTraverse;		//squares the king traverses to castle kingside (must all be free of check) excludes start and end square.
	public long KingFrom;			//where the king came from
	public long KingTo;			//where the king ends up	
	public long RookFrom;			//where the rook comes from
	public long RookTo;			//where the room ends up
}
