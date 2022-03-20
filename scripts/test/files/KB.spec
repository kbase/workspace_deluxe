/*
test module for scripts
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
	@searchable ws_subset id feature_ids
	*/
	typedef structure {
		string id;
		string name;
		string sequence;
		list <feature_id> feature_ids;
	} Genome;

	/*
	*/
	typedef structure {
		string name;
		string annotation;
		list <feature_id> feature_ids;
	} FeatureGroup;


	typedef structure {
		string name;
		UnspecifiedObject stuff;
	} RandomObject;


};


