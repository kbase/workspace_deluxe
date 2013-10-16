/*
test module for type validation with annotations
*/
module KB {


	/*
	this is an id that can point to any ws object
	@id_reference
	*/
	typedef string kbid;

	/*
	@id_reference KB.Genome
	*/
	typedef string genome_id;

	/*
	@id_reference KB.Feature
	*/
	typedef string feature_id;

	/*
	*/
	typedef structure {
		string name;
		string sequence;
	} Feature;

	

	/*
	@ws_searchable name feature_ids
	@ws_searchable keys_of regulators
	*/
	typedef structure {
		string name;
		string sequence;
		feature_id bestFeature;
		list <feature_id> feature_ids;
		mapping <feature_id, int> length_of_features;
		mapping <feature_id,list<feature_id>> regulators;
	} Genome;



};


