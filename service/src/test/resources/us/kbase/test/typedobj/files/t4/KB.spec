/*
test module for type validation with annotations
*/
module KB {


	/*
	this is an id that can point to any ws object
	@id kb
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
		string name;
		string sequence;
	} Feature;


	/*
	@searchable ws_subset featmap.*.name
	@searchable ws_subset name feature_ids secondBest.name,feats.[*].sequence
	@searchable ws_subset keys_of(regulators)
	*/
	typedef structure {
		kbid source;
		string name;
		string sequence;
		feature_id bestFeature;
		Feature secondBest;
		list <Feature> feats;
		mapping <feature_id,Feature> featmap;
		list <feature_id> feature_ids;
		mapping <feature_id, int> length_of_features;
		mapping <feature_id,list<feature_id>> regulators;
	} Genome;



};


