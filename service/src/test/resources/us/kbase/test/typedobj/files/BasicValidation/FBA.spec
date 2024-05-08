
#include <KB.spec>

/*
test module for type validation with annotations
*/
module FBA {


	/*
	@id ws FBA.FBAModel
	*/
	typedef string fba_model_id;

	
	/* 
	@searchable ws_subset id name genome
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
	@searchable ws_subset model fluxes
	*/
	typedef structure {
		fba_model_id model;
		list <float> fluxes;
	} FBAResult;


};


