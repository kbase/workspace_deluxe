package us.kbase.workspace.kbase;

import static java.util.Objects.requireNonNull;
import static us.kbase.workspace.database.Util.checkString;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.core.type.TypeReference;

import us.kbase.common.service.JsonClientCaller;
import us.kbase.common.service.JsonClientException;
import us.kbase.common.service.RpcContext;
import us.kbase.sampleservice.GetSampleACLsParams;
import us.kbase.sampleservice.SampleACLs;
import us.kbase.sampleservice.SampleServiceClient;
import us.kbase.sampleservice.SampleServiceClientDynamic;
import us.kbase.sampleservice.UpdateSampleACLsParams;

/**
 * A wrapper around the generic KBase SDK client for the sample service. The standard clients
 * are not suitable here as the client may need to operate in dynamic or standard mode depending
 * on whether the sample service is accessed directly (for example, in tests) or via the KBase
 * service wizard, and the standard clients are hard coded to one or the other.
 * 
 * Currently only methods required for workspace / sample service integration are implemented and
 * are essentially duplicated from the SDK clients.
 * The method documentation, if any, is duplicated from the SDK clients.
 * 
 * Altering the provided {@link JsonClientCaller} after passing it into the wrapper may cause
 * undefined behavior.
 * 
 * @see JsonClientCaller
 * @see SampleServiceClient
 * @see SampleServiceClientDynamic
 */
public class SampleServiceClientWrapper {

	private final JsonClientCaller caller;
	private final Optional<String> serviceVersion;
	
	/** Create the client in standard mode. The caller should be initialized with the direct URL
	 * to the sample service.
	 * @param caller the generic client.
	 */
	public SampleServiceClientWrapper(final JsonClientCaller caller) {
		this.caller = requireNonNull(caller, "caller");
		this.caller.setDynamic(false);
		this.serviceVersion = Optional.empty();
	}
	
	/** Create the client in dynamic mode. The caller should be initialized with the URL of the
	 * KBase service wizard.
	 * @param caller the generic client.
	 * @param serviceVersion the service tag for the Sample Service, e.g. release, beta, dev, a
	 * version, or a git hash.
	 */
	public SampleServiceClientWrapper(final JsonClientCaller caller, final String serviceVersion) {
		this.caller = requireNonNull(caller, "caller");
		this.caller.setDynamic(true);
		this.serviceVersion = Optional.of(checkString(serviceVersion, "serviceVersion"));
	}
	
	/** Get a sample service client.
	 * @param caller the generic client.
	 * @param serviceVersion null or empty to get a standard client, or a service tag to get a
	 * dynamic client.
	 * @return the client.
	 */
	public static SampleServiceClientWrapper getClient(
			final JsonClientCaller caller,
			final Optional<String> serviceVersion) {
		if (serviceVersion == null || serviceVersion.isEmpty() || serviceVersion.get().isBlank()) {
			return new SampleServiceClientWrapper(caller);
		} else {
			return new SampleServiceClientWrapper(caller, serviceVersion.get());
		}
	}

	/**
	 * <p>Original spec-file function name: get_sample_acls</p>
	 * <pre>
	 * Get a sample's ACLs.
	 * </pre>
	 * @param params instance of type
	 * {@link us.kbase.sampleservice.GetSampleACLsParams GetSampleACLsParams}
	 * @return parameter "acls" of type {@link us.kbase.sampleservice.SampleACLs SampleACLs}
	 * @throws IOException if an IO exception occurs
	 * @throws JsonClientException if a JSON RPC exception occurs
	 */
	public SampleACLs getSampleAcls(GetSampleACLsParams params, RpcContext... jsonRpcContext)
			throws IOException, JsonClientException {
		List<Object> args = new ArrayList<Object>();
		args.add(params);
		TypeReference<List<SampleACLs>> retType = new TypeReference<List<SampleACLs>>() {};
		List<SampleACLs> res = caller.jsonrpcCall(
				"SampleService.get_sample_acls",
				args,
				retType,
				true,
				false,
				jsonRpcContext,
				this.serviceVersion.orElse(null));
		return res.get(0);
	}

	/**
	 * <p>Original spec-file function name: update_sample_acls</p>
	 * <pre>
	 * Update a sample's ACLs.
	 * </pre>
	 * @param params instance of type
	 * {@link us.kbase.sampleservice.UpdateSampleACLsParams UpdateSampleACLsParams}
	 * @throws IOException if an IO exception occurs
	 * @throws JsonClientException if a JSON RPC exception occurs
	 */
	public void updateSampleAcls(UpdateSampleACLsParams params, RpcContext... jsonRpcContext)
			throws IOException, JsonClientException {
		List<Object> args = new ArrayList<Object>();
		args.add(params);
		TypeReference<Object> retType = new TypeReference<Object>() {};
		caller.jsonrpcCall(
				"SampleService.update_sample_acls",
				args,
				retType,
				false,
				true,
				jsonRpcContext,
				this.serviceVersion.orElse(null));
	}

	public Map<String, Object> status(RpcContext... jsonRpcContext)
			throws IOException, JsonClientException {
		List<Object> args = new ArrayList<Object>();
		TypeReference<List<Map<String, Object>>> retType =
				new TypeReference<List<Map<String, Object>>>() {};
		List<Map<String, Object>> res = caller.jsonrpcCall(
				"SampleService.status",
				args,
				retType,
				true,
				false,
				jsonRpcContext,
				this.serviceVersion.orElse(null));
		return res.get(0);
	}

}
