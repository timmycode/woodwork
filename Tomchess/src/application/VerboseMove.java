package application;

import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;

import application.Board.Color;
import application.Board.MoveHistoryNode;

public class VerboseMove extends Move {
	public VerboseMove(Move o, Board context) {
		super(o);
		
		if (o == null) { 
			System.err.println("Null move in VerboseMove()!");
			return;
		}
		if (o != null) {
			toSquare 	= Board.slowMaskToIndex(toMask);
			fromSquare 	= Board.slowMaskToIndex(fromMask);
			
			name = "";
			Board.ColoredPiece fromPiece = context.getPieceAt(o.fromMask);
			
			LinkedList<Move> legalMoves = context.getLegalMoves();
			
			boolean isLegal = false;
			for (Move m : legalMoves) {
				if (m.compare(o)) {
					isLegal = true;
				}
			}
			if (isLegal == false) {
				System.err.println("ILLEGAL MOVE (" + o.toString() + ") on board\n" + context.renderState(""));
				return;
			}
			
			if (fromPiece.c == Color.EMPTY) {
				name = "BAD MOVE";
			} else {
				mover = fromPiece.c;
			}
			if (o.isCastle) {
				if (o.cm == CastleMovePattern.WHITE_KING_SIDE || o.cm == CastleMovePattern.BLACK_KING_SIDE) {
					name = "0-0";
				} else {
					name = "0-0-0";
				}
			} else {
				if (fromPiece.p == Piece.PAWN) {
					if (o.isCapture) {
						name = "" + Board.squareName( fromSquare ).charAt(0) + "x" + Board.squareName( toSquare );
					} else {
						name = Board.squareName( toSquare );
					}
					if (o.isPromotion) {
						name += o.promotionPiece.fen.toUpperCase();
					}
				}
				else
				{
					name = fromPiece.p.fen.toUpperCase();
					if (o.isCapture) {
						name += "x";
					}
					name += Board.squareName(toSquare);
				}
		}


		context.pushMove(o);
		if (context.isCheck(context.getToPlay())) {
			boolean mate = context.isCheckMate();
			if (context.isCheckMate()) {
				name += "#";
				System.err.println(" is mate = " + mate);
			} 
			else {
				name += "+";
			}
		}
		context.popMove();
		}
	}

	public String algebraic() {
		return name;
	}
	Color mover;
	private String name;
	private int toSquare;
	private int fromSquare;
	
	public static ArrayList<VerboseMove> getMoveHistoryVerbose(Board startBoard, Deque<Move> moveHistoryList) {		
		ArrayList<VerboseMove> r = new ArrayList<VerboseMove>();
		
		for (Iterator<Move> I = moveHistoryList.iterator(); I.hasNext();) {
			Move m = I.next();
			r.add(new VerboseMove(m,startBoard));
			startBoard.pushMove(m);
		}
		
		for (Iterator<Move> I = moveHistoryList.descendingIterator(); I.hasNext();) {
			I.next();
			startBoard.popMove();
		}		
		
		return r;
	}
	
}
