package us.kbase.typedobj.tests;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.github.fge.jsonschema.report.LogLevel;
import com.github.fge.jsonschema.report.ProcessingMessage;
import com.github.fge.jsonschema.report.ProcessingReport;

import us.kbase.typedobj.core.AbsoluteTypeDefId;
import us.kbase.typedobj.core.TypedObjectValidationReport;
import us.kbase.typedobj.exceptions.RelabelIdReferenceException;
import us.kbase.typedobj.idref.IdReference;
import us.kbase.typedobj.idref.WsIdReference;

public class DummyTypedObjectValidationReport extends
		TypedObjectValidationReport {

	public DummyTypedObjectValidationReport() {
		super(null, null, null);
	}


	@Override
	public AbsoluteTypeDefId getValidationTypeDefId() {
		throw new RuntimeException("cannot get the AbsoluteTypeDefId from a dummy typed object validation report.");
	}
	
	@Override
	public boolean isInstanceValid() {
		return true;
	}
	
	@Override
	public int getErrorCount() {
		return 0;
	}
	
	@Override
	public List <String> getErrorMessagesAsList() {
		return new ArrayList<String>();
	}
	
	
	@Override
	public ProcessingReport getRawProcessingReport() {
		throw new RuntimeException("cannot get the processing report from a dummy typed object validation report.");
	}
	
	@Override
	public List<WsIdReference> getWsIdReferences() {
		return new ArrayList<WsIdReference>();
	}
	@Override
	public List<IdReference> getAllIdReferences() {
		return new ArrayList<IdReference>();
	}
	@Override
	public List<IdReference> getAllIdReferencesOfType(String type) {
		return new ArrayList<IdReference>();
	}
	
	
	@Override
	public String toString() { 
		return "DummyTypedObjectValidationReport";
	}
	
}
