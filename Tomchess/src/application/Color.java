package application;

public enum Color {
	WHITE, BLACK, EMPTY;

	public int sign() {
		switch (this) {
		case WHITE:
			return +1;
		case BLACK:
			return -1;
		default:
			return 0;
		}
	}
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
