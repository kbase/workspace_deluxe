
module KB {


	/*
	  @optional size
	  @searchable ws_subset name,alias,size,width
	  aa@searchable ws_subset size
	  aa@searchable ws_subset width
	*/
	typedef structure {
		string name;
		string alias;
		int size;
		int maxsize;
		float width;
		float maxwidth;
	} SimpleStructure;


	/*
	  @searchable ws_subset numbers,floaters.*
	  aa@searchable ws_subset floaters.*
	*/
	typedef structure {
		mapping<string,int> numbers;
		mapping<string,float> floaters;
		string throwAwayData;
	} MappingStruct;
	
	
	/*
	  @searchable ws_subset names,numbers.[*]
	  aa@searchable ws_subset numbers.[*]
	*/
	typedef structure {
		list<string> names;
		list<int> numbers;
		string throwAwayData;
	} ListStruct;


	/*
	  @searchable ws_subset keys_of(crazydata.*.*)
	*/
	typedef structure {
		mapping<string,mapping<string,mapping<string,int>>> crazydata;
	} DeepMaps;


	/*
	*/
	typedef structure {
		string name;
		string seq;
		int length;
	} Subdata;

	/*
	@optional dl dm dml
	@searchable ws_subset d.(name,length),dl.[*].name,dm.*.(seq,length),dml.*.[*].name
	a@searchable ws_subset dl.[*].name
	a@searchable ws_subset dm.*.(seq,length)
	a@searchable ws_subset dml.*.[*].name
	*/
	typedef structure {
		Subdata d;
		list<Subdata> dl;
		mapping<string,Subdata> dm;
		mapping<string,list<Subdata>> dml;
	} NestedData;

};


