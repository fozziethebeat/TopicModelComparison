# Detailed Setup for Exploring Topic Coherence over many models and many topics

We (Keith Stevens, Philip Kegelmeyer, David Andrzejewski, and David Buttler)
published the paper [Exploring Topic Coherence over many models and many topics][1] 
which compares several topic models using a variety of measures in an attempt to
determine which model should be used in which application.  This evaluation
secondly compares automatic coherence measures as a quick, task free method for
comparing a variety of models.  Below is a detailed series of steps on how to
replicate the results from the paper.

The evaluation setup breaks down into the following steps:

1. Select a corpus and pre-process.
2. Remove stop words, infrequent words, and format the corpus.
3. Perform topic modelling on all documents
4. Compute topic coherence measures for induced topics
5. Compute word similarities using semantic pairing tests
6. Compute Classifier accuracy using induced topics

Each of these steps are automated in the bash scripts provided in this
repository.  To run those scripts read the last section for downloading the
needed components, setting parameters, and then watching the scripts blaze
through the setup.

The rest of this writeup explains each step in more detail than was permitted in
the published paper.

## Selecting the corpus

The evaluation requires the use of a semantically labeled corpus that has a
relatively cohesive focus.  The original paper used all articles from 2003 of
the [New York Times Annotated Corpus][2] provided by the [Linguistics Data Consortium][3].  
Any similarly structured corpus should work.  

The New York Times corpus requires some pre-processing before it can be easily
used in the evaluation.  The original corpus comes in a series of tarballed xml
files where each file looks something like this:

``` xml
<nitf change.date="month day, year" change.time="HH:MM" version="-//IPTC//DTD NITF 3.3//EN">
<head>
  <title>Article Title</title>
  <meta content="Section Name" name="online_sections"/>
</head>
<body>
  <body.contents>
    <block class="full_text">
      <p>Article text</p>
    </block>
  </body.contents>
</body>
</nitf>
```
This leaves out a lot of details, but it covers the key items we will need: (1)
the full text of the article and (2) all online\_sections for the article.
Extracting this can be kinda hairy.  The following snippet gives a gist of how
to extract and format the neccesary data:

``` scala
import scala.xml.XML

val doc = XML.loadFile(file)
val sections = (doc \\ "meta").filter(node => (node \ "@name").text == "online_sections")
                              .map(node => (node \ "@content").text)
                              .mkString(";")
val text = (doc \\ "block").filter(node => (node \ "@class").text == "full_text")
                           .flatMap(node => (node \ "p").map(_.text.replace("\n", " ").trim))
                           .mkString(" ")
```

Before printing the data, we also need to tokenize everything.  We used the Open
NLP MaxEnt tokenizers.  First download the english MaxEnt tokenizer model
[here][4] then do the following before processing each document

``` scala
val tokenizerModel = new TokenizerModel(new FileInputStream(modelFileName))
val tokenizer = new TokenizerME(tokenizerModel)
val stopWords = Source.fromFile(args(1)).getLines.toSet
def acceptToken(token: String) = !stopWords.contains(token)
```

And then do the following to each piece of `text` extracted:

```
val tokenizedText = tokenizer.toLowerCase.tokenize(text).filter(acceptToken).mkString(" ")
printf("%s\t%s\n", sections, tokenizedText)
```

This should generate one line per document in the format


```
section_1(;section_n)+<TAB>doc_text   
```

With properly tokenized text and a series of stop words removed..

## Filtering tokens

In order to limit the memory requirements of our processing steps, we discard
any word that is not in the list of word similarity pairs or the top 100k most
frequent tokens in the corpus.  The following bash lines will accomplish this:

```
cat $oneDocFile | cut -f 2 | tr " " "\n" | sort | uniq -c | \ 
                  sort -n -k 1 -r | head -n 100000 | \
                  awk '{ print $2 }' > $topTokens
cat wordsim*.terms.txt $topTokens | uniq > .temp.txt
mv .temp.txt $topTokens
```

Once we've gotten the top tokens that'll be used during processing, we do one
more filtering of the corpus to reduce each document down to only the accepted
words and discard any documents that contain no useful content words.  Running
[FilterCorpus][4] with the top tokens file and the corpus file will return a
properly filtered corpus.

## Topic Modeling

With all the pre-processing completed, we can now generate topics for the
corpus.  We do this using two different methods (1) Latent Dirichlet Allocation
and (2) Latent Semantic Analsysis

### Processing for Latent Dirichlet Allocation


### Processing for Latent Semantic Analysis

Latent Semantic Analysis at it's core decomposes a term by document matrix into
two smaller latent matrices using one of two methods: (1) [Singular Value
Decomposition][] and (2) [Non-negative Matrix Factorization][].  We do this in
two steps:

1. Build a weighted term document matrix.
2. Factorize the matrix using either SVD or NMF.

We do the first step using the [BuildTermDocMatrix][] method, which first builds
up a raw term document matrix that counts the number of times each word occurs
in each document.  It then performs a matrix transformation, in our case we use
the [Log Entropy][] transform, over the matrix and saves it to disk in a [Sparse Matlab Format][].  

We do the second step using the [ReduceMatrix][] method which simply decomposes
the weighted term document matrix using the desired method, either "nmf" or
"svd", and saves two matrices to disk: a word by topic matrix and a document by
topic matrix.  Run this method for each number of topics you wish to generate
using both methods.   At the end there should be two sets of matrics: word to
topic matrices and document to topic matrices.

  [1]: 
  [2]: http://www.ldc.upenn.edu/Catalog/catalogEntry.jsp?catalogId=LDC2008T19
  [3]: http://www.ldc.upenn.edu/
  [4]: http://opennlp.sourceforge.net/models-1.5/en-token.bin
