package application;

public class FenWriter {
	private String data;
	private int spaceCount;
	
	@Override
	public String toString() {
		return data;
	}
	
	private void flushSpaces() {
		if (spaceCount > 0) {
			data += ""+spaceCount;
		}
		spaceCount = 0;
	}
	
	private void writeSquare(ColoredPiece cp) {
		if (cp.c == Color.EMPTY) {
			spaceCount += 1;
		} else if (cp.c == Color.BLACK) {
			flushSpaces();
			data += cp.p.fen.toLowerCase();
		} else if (cp.c == Color.WHITE) {
			flushSpaces();
			data += cp.p.fen.toUpperCase();
		}
	}
	 
	public FenWriter(Board b) {
		int y = 7;
		int x = 0;
		
		data = "";
		//write the piece representation
		while (Board.isWithinBoard(x, y)) {
			writeSquare(b.getPieceAt(x,y));
			//increment square
			x += 1;
			if (x > 7) {
				flushSpaces();
				if (y != 0) {
					data += "/";
				}
				x = 0;
				y--;
			}
		}
		
		//write the side to move
		data += " ";
		if (b.getToPlay() == Color.BLACK) {
			data += "b";
		} 
		else {
			data += "w";
		}
		
		//write castling rights
		data += " ";
		if (b.canCastle(CastleMovePattern.WHITE_KING_SIDE)) {
			data += "K";
		}
		if (b.canCastle(CastleMovePattern.WHITE_QUEEN_SIDE)) {
			data += "Q";
		}		
		if (b.canCastle(CastleMovePattern.BLACK_KING_SIDE)) {
			data += "k";
		}
		if (b.canCastle(CastleMovePattern.BLACK_KING_SIDE)) {
			data += "q";
		}		
		
		//write enpassant square
		if (b.getEnpassantMask() != 0){
			int square = Board.slowMaskToIndex(b.getEnpassantMask());
			data += " ";
			data += Board.squareName(square);
		}
	}
}
