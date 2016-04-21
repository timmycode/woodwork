package application;

public class Move {
	public long fromMask;
	public long toMask;

	public boolean isPromotion;
	public boolean isCheck;
	public boolean isCapture;
	
	public boolean nullMove;
	
	public long enPassantCap;
	public boolean isEnPassant() {
		return enPassantCap != 0;
	}
	public long captureSquare() {
		if (isCapture) {
			if (isEnPassant()) {
				return enPassantCap;
			} 
			else {
				return toMask;
			}
		}
		return 0;
	}
	
	public long getCaptureMask() {
		if (isCapture) {
			if (enPassantCap != 0) {
				return enPassantCap;
			}
			else {
				return toMask;
			}
		}
		else {
			return 0;
		}
	}
	
	public boolean isCastle;
	public CastleMovePattern cm;
	
	public Piece promotionPiece;
	
	public Move(Move o) {
		super();
		this.nullMove = o.nullMove;
		this.fromMask = o.fromMask;
		this.toMask = o.toMask;
		this.isPromotion = o.isPromotion;
		this.isCheck = o.isCheck;
		this.isCapture = o.isCapture;
		this.isCastle = o.isCastle;
		this.cm = o.cm;
		this.promotionPiece = o.promotionPiece;
		this.enPassantCap = o.enPassantCap;
	}

	public Move(long _fromMask, long _toMask) {
		nullMove = false;
		
		fromMask = _fromMask;
		toMask = _toMask;		
		
		isPromotion = false;
		isCheck = false;
		isCapture = false;
		
		enPassantCap = 0L;
	
		isCastle = false;
		cm = null;
	}
	
	public boolean compare(Move rhs) {
		if (fromMask == rhs.fromMask) {
			if (this.isPromotion) {
				if (rhs.isPromotion == false) {
					return false;
				}
				if (this.promotionPiece != rhs.promotionPiece) {
					return false;
				}
			}
			if (toMask == rhs.toMask) {
				return true;
			}
		}
		return false;
	}
	
	@Override
	public String toString() {
		if (nullMove) {
			return "NullMove";
		}
		String m = " - ";
		if (isCapture) {
			m = " x ";
		}		
		String r = Board.squareName( Board.slowMaskToIndex(fromMask)) + m + Board.squareName( Board.slowMaskToIndex(toMask));
		return r;
	}
}
