package us.kbase.typedobj.core.validatorconfig;

import com.github.fge.jsonschema.cfg.ValidationConfiguration;
import com.github.fge.jsonschema.library.DraftV4Library;
import com.github.fge.jsonschema.library.Keyword;
import com.github.fge.jsonschema.library.Library;
import com.github.fge.jsonschema.library.LibraryBuilder;
import com.github.fge.jsonschema.messages.JsonSchemaValidationBundle;
import com.github.fge.msgsimple.bundle.MessageBundle;
import com.github.fge.msgsimple.bundle.MessageBundleBuilder;
import com.github.fge.msgsimple.load.MessageBundles;
import com.github.fge.msgsimple.source.MessageSource;



/**
 * JSON schema validation works by creating a JsonSchema object that
 * can validate json instances.  JsonSchema objects are built with the
 * JsonSchemaFactory class.  This class, in turn, can be initialized to
 * a custom configuration so that special keywords can be validated.
 * 
 * This class provides a static method for generating a custom configuration
 * that is designed to validate KBase typed objects in conjunction with
 * the Workspace deluxe.
 * 
 * @author msneddon
 *
 */
public class ValidationConfigurationFactory {
	
	/**
	 * This method will create you a new ValidationConfiguration all set up
	 * for validating KBase Workspace Objects
	 * @return
	 */
	public static ValidationConfiguration buildKBaseWorkspaceConfiguration() {
		
		/*
		 *  Step 1: create all the keywords that we want to handle by using
		 *  the getKeyword method from our ValidationBuilders
		 */
		final Keyword originalTypeKeyword    = OriginalTypeValidationBuilder.getKeyword();
		final Keyword idRefKeyword           = IdRefValidationBuilder.getKeyword();
		final Keyword searchableKeyword      = SearchableWsSubsetValidationBuilder.getKeyword();
		
		 /*
		 * Step 2: Fetch the default Library based on Json Schema V4, thaw it, add
		 * our keywords, freeze it again and we are ready to go.
		 */
		LibraryBuilder kbLibBuilder = DraftV4Library.get().thaw();
		kbLibBuilder.addKeyword(originalTypeKeyword);
		kbLibBuilder.addKeyword(idRefKeyword);
		kbLibBuilder.addKeyword(searchableKeyword);
		final Library kbLibrary = kbLibBuilder.freeze();
		
		
		/*
		 * Step 3: Complement the validation message bundle with error messages that can later
		 * be attached to the validator report (note that this is not required, but is a
		 * nice feature to ensure consistent error messages, which we don't use currently)
		 */
		final MessageSource originalTypeMssgs   = OriginalTypeValidationBuilder.getErrorMssgSrc();
		final MessageSource idRefMssgs          = IdRefValidationBuilder.getErrorMssgSrc();
		final MessageSource searchableMssgs     = SearchableWsSubsetValidationBuilder.getErrorMssgSrc();
		
		MessageBundleBuilder mbb = MessageBundles.getBundle(JsonSchemaValidationBundle.class).thaw();
		mbb.appendSource(originalTypeMssgs);
		mbb.appendSource(idRefMssgs);
		mbb.appendSource(searchableMssgs);
		final MessageBundle bundle = mbb.freeze();
		
		/*
		 * Step 4: Create the new ValidationConfiguration and set the kbase library as default
		 * TODO define the json schema for our json schema, and post the library online somewhere....
		 */
		ValidationConfiguration cfg = 
				ValidationConfiguration.newBuilder()
				.setDefaultLibrary("http://kbase.us/docs/typedobj/schema#", kbLibrary)
				.setValidationMessages(bundle)
				.freeze();
		
		return cfg;
	}
	
	
	
	
	
}
