package application;


public enum EnumOutcome {
	STILL_PLAYING		(false,	false, 		"still playing"),
	DRAW_STALEMATE 		(true,	true, 		"draw by stalemate"),
	WHITE_WIN_CHECKMATE	(true,	false,		"white wins by checkmate"),
	BLACK_WIN_CHECKMATE	(true,	false, 		"black wins by checkmate"),
	DRAW_REPETITION		(true,	true,		"draw by repetition"),
	DRAW_MATERIAL		(true,	true,		"draw by insufficient material");

	private boolean drawn; 
	private String description;
	private boolean isGG;
	
	private EnumOutcome(boolean _isGG, boolean _drawn, String _description) {
		drawn = _drawn;
		isGG = _isGG;
		description = _description;
	}
	
	public boolean isGameOver() {
		return isGG;
	}
	
	public String getDescription() { 
		return description;
	}
	
	public boolean isDraw() {
		return drawn;
	}
	
}
