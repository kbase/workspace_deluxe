/*
@author chenry
*/
module KBGA {
    /*
    	Reference to a ContigSet object containing the contigs for this genome in the workspace
		@id ws KBGA.ContigSet
	*/
    typedef string contigset_ref;
    /*
		Reference to a ProteinSet object containing the proteins for this genome in the workspace
		@id ws KBGA.ProteinSet
	*/
    typedef string proteinset_ref;
    /*
		Reference to a TranscriptSet object containing the transcripts for this genome in the workspace
		@id ws KBGA.TranscriptSet
	*/
    typedef string transcriptset_ref;
    /*
		Reference to a source_id
		@id external SEED GenBank MicrobesOnline
	*/
    typedef string source_id;
    /*
		KBase genome ID
		@id kb
	*/
    typedef string genome_id;
    /*
		Genome feature ID
		@id external
	*/
    typedef string feature_id;
    /*
		Reference to an individual contig in a ContigSet object
		@id subws KBGA.ContigSet.contigs.[].id
	*/
    typedef string contig_ref;
    /*
		ContigSet contig ID
		@id external
	*/
    typedef string contig_id;
    /*
		KBase contig set ID
		@id kb
	*/
    typedef string contigset_id;
    /*
		Reference to a source_id
		@id external
	*/
    typedef string source_id;
    /*
		Reference to a reads file in shock
		@id shock
	*/
    typedef string reads_ref;
    /*
		Reference to a fasta file in shock
		@id shock
	*/
    typedef string fasta_ref;
    
    /* Type spec for a "Contig" subobject in the "ContigSet" object
	
		contig_id id - ID of contig in contigset
		string md5 - unique hash of contig sequence
		string sequence - sequence of the contig
	    string description - Description of the contig (e.g. everything after the ID in a FASTA file)
		
		@optional description
    	@searchable ws_subset id md5
	*/
	typedef structure {
		contig_id id;
		string md5;
		string sequence;
		string description;
    } Contig;
	
	/* Type spec for the "ContigSet" object
	
		contigset_id id - unique kbase ID of the contig set
		string name - name of the contig set
		string type - type of the contig set (values are: Organism,Environment,Collection)
		source_id source_id - source ID of the contig set
		string source - source of the contig set
		list<Contig> contigs - list of contigs in the contig set
		reads_ref reads_ref - reference to the shocknode with the rawreads from which contigs were assembled
		fasta_ref fasta_ref - reference to fasta file from which contig set were read
		
		@optional name type reads_ref fasta_ref
    	@searchable ws_subset contigs.[*].(id,md5) md5 id name source_id source type
	*/
	typedef structure {
		contigset_id id;
		string name;
		string md5;
		source_id source_id;
		string source;
		string type;
		reads_ref reads_ref;
		fasta_ref fasta_ref;
		list<Contig> contigs;
    } ContigSet;
    
	/*
		Type of a genome feature with possible values peg, rna
	*/
    typedef string feature_type;
    /* A region of DNA is maintained as a tuple of four components:

		the contig
		the beginning position (from 1)
		the strand
		the length

	   We often speak of "a region".  By "location", we mean a sequence
	   of regions from the same genome (perhaps from distinct contigs).
        */
    typedef tuple<contig_ref contig_id, int begin, string strand,int length> region_of_dna;
    /*
		a "location" refers to a list of regions of DNA on contigs
    */
    typedef list<region_of_dna> location;
    /*
		a notation by a curator of the genome object
    */
    typedef tuple<string comment, string annotator, int annotation_time> annotation;

    /* 
    	Structure for a single feature of a genome
    	
		@optional function protein_translation
		@searchable ws_subset id type function aliases md5
    */
    typedef structure {
		feature_id id;
		location location;
		feature_type type;
		string function;
		string md5;
		string protein_translation;
		list<string> aliases;
		list<annotation> annotations;
    } Feature;
    
    /* 
    	Genome object holds much of the data relevant for a genome in KBase
    	
    	@optional contigset_ref proteinset_ref transcriptset_ref
    	@searchable ws_subset features.[*].(md5,id,type,function,aliases) source_id source genetic_code id scientific_name domain contigset_ref proteinset_ref transcriptset_ref
    */
    typedef structure {
		genome_id id;
		string scientific_name;
		string domain;
		int genetic_code;
		string source;
		source_id source_id;
		list<Feature> features;
		
		contigset_ref contigset_ref;
		proteinset_ref proteinset_ref;
		transcriptset_ref transcriptset_ref;
    } Genome;
};
