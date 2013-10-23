package us.kbase.typedobj.tests;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.github.fge.jsonschema.report.ProcessingReport;

import us.kbase.typedobj.core.AbsoluteTypeDefId;
import us.kbase.typedobj.core.IdReference;
import us.kbase.typedobj.core.TypedObjectValidationReport;

public class DummyTypedObjectValidationReport extends
		TypedObjectValidationReport {

	public DummyTypedObjectValidationReport() {
		super(null, null);
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
	public String [] getErrorMessages() {
		return new String[0];
	}
	
	
	@Override
	public ProcessingReport getRawProcessingReport() {
		throw new RuntimeException("cannot get the processing report from a dummy typed object validation report.");
	}
	
	@Override
	public List<List<IdReference>> getListOfIdReferenceObjects() {
		return new LinkedList<List<IdReference>>();
	}
	
	@Override
	public List<String> getListOfIdReferences() {
		return new LinkedList<String>();
	}
	
	
	@Override
	public void setAbsoluteIdReferences(Map<String,String> absoluteIdRefMapping) { }
	
	@Override
	public String toString() { 
		return "DummyTypedObjectValidationReport";
	}
	
}
