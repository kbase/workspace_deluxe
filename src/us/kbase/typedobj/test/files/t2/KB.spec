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

};


