/*
test module for type validation with annotations
*/
module KB {


	/*
	@id ws
	*/
	typedef string kbid;

	/*
	@id ws KB.Genome
	*/
	typedef string genome_id;

	/*
	@id ws KB.Feature
	*/
	typedef string feature_id;

	/*
	*/
	typedef structure {
		string id;
		string name;
		string type;
		int length;
	} Feature;

	/*
	
	*/
	typedef structure {
		genome_id genome;
		string description;
		list <feature_id> interesting_things;
	} RelatedGenome;

	/*
	*/
	typedef structure {
		string id;
		string name;
		string sequence;
		list <feature_id> feature_ids;
		mapping <feature_id, int> length_of_features;
		list <RelatedGenome> related_genomes;
	} Genome;

	/*
	*/
	typedef structure {
		string name;
		string annotation;
		list <feature_id> feature_ids;
	} FeatureGroup;


	typedef structure {
		mapping <feature_id,feature_id> fids;
	} FeatureMap;

	typedef structure {
	
		mapping<feature_id,mapping<feature_id,tuple<feature_id,list<feature_id>,feature_id>>> crazy;
	
	} DeepFeatureMap;

	typedef structure {
		mapping<string, mapping<string, feature_id>> nested_features;
	} NestedFeaturesValue;

	typedef structure {
		mapping<string, mapping<feature_id, string>> nested_features;
	} NestedFeaturesKey;

	typedef structure {
		list<mapping<string, feature_id>> nested_features;
	} NestedFeaturesList;

	typedef structure {
		feature_id id;
	} ID;
	
	typedef structure {
		string id;
	} NoID;
	
	typedef structure {
		ID id;
	} WithID;
	
	typedef structure {
		NoID id;
	} WithNoID;
	
	typedef structure {
		WithNoID id1;
		WithID id2;
		WithNoID id3;
		WithNoID id4;
		WithNoID id5;
		WithID id6;
		WithNoID id7;
		WithNoID id8;
	} AltIDs;

	typedef structure {
		tuple<feature_id fid, int foo, UnspecifiedObject uo, mapping<string,
			UnspecifiedObject> bar, feature_id fid2> t;
	} WeirdTuple;


};


