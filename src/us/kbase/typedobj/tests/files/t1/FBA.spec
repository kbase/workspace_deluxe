
#include <KB.spec>

/*
test module for type validation with annotations
*/
module FBA {


	/*
	@id_reference FBA.FBAModel
	*/
	typedef string fba_model_id;

	/*
	@ws_searchable id name genome
	@optional description
	*/
	typedef structure {
		string id;
		string name;
		string description;
		KB.genome_id genome;
		list <string> reactions;
	} FBAModel;

	/*
	@ws_searchable model fluxes
	*/
	typedef structure {
		fba_model_id model;
		list <float> fluxes;
	} FBAResult;


};


