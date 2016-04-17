package application;


public enum Piece {
	PAWN 	(0,"p", "♙","♟",1),
	KNIGHT 	(1,"n", "♘","♞",3),
	BISHOP 	(2,"b", "♗","♝",3),
	ROOK 	(3,"r", "♖","♜",5),
	QUEEN 	(4,"q", "♕","♛",9),
	KING 	(5,"k", "♔","♚",0);
	
	public String fen;
	public String unicodeWhite, unicodeBlack;
	public int naiveValue;
	public int index;
	
	private Piece (int _index, String _fen, String _uniWhite, String _uniBlack, int _naiveValue) {
		index = _index;
		fen = _fen;
		unicodeWhite = _uniWhite;
		unicodeBlack = _uniBlack;
		naiveValue = _naiveValue;
	}
}