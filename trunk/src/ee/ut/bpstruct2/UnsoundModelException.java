package ee.ut.bpstruct2;

import ee.ut.bpstruct.CannotStructureException;

public class UnsoundModelException extends CannotStructureException {
	private static final long serialVersionUID = -5533236002025313388L;
	public UnsoundModelException(String msg) {
		super(msg);
	}
}
