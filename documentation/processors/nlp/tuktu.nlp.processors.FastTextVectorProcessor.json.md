### tuktu.nlp.processors.FastTextVectorProcessor
Loads pre-trained fastText models and uses them to induce a vector for a sentence.

  * **id** *(type: string)* `[Required]`

  * **result** *(type: string)* `[Required]`

  * **config** *(type: object)* `[Required]`

    * **model_name** *(type: string)* `[Required]`
    - The name of the model to load from disk

    * **tokens_field** *(type: string)* `[Required]`
    - The field containing the tokens that form the sentence to convert to a vector

    * **learn_rate** *(type: double)* `[Optional, default = 0.05]`
    - The learning rate

    * **learn_rate_update_rate** *(type: int)* `[Optional, default = 100]`
    - Updates for the learning rate 

    * **vector_size** *(type: int)* `[Optional, default = 100]`
    - Size of the word vectors

    * **window_size** *(type: int)* `[Optional, default = 5]`
    - Size of the context window

    * **epochs** *(type: int)* `[Optional, default = 5]`
    - The number of epochs

    * **min_count** *(type: int)* `[Optional, default = 5]`
    - The minimum occurrence count of a word

    * **min_count_label** *(type: int)* `[Optional, default = 0]`
    - The minimum occurrence count of a label

    * **negative** *(type: int)* `[Optional, default = 5]`
    - Number of negatives sampled

    * **word_n_grams** *(type: int)* `[Optional, default = 1]`
    - Max length of the word N-grams

    * **loss_name** *(type: string)* `[Optional, default = "ns"]`
    - Loss function name (ns, hs, softmax)

    * **ft_model_name** *(type: string)* `[Optional, default = "sg"]`
    - Name of the fastText model to use (cbow, sg, sup)

    * **buckets** *(type: int)* `[Optional, default = 2000000]`
    - Number of buckets to use

    * **min_n_gram** *(type: int)* `[Optional, default = 3]`
    - Minimum length of the character N-grams

    * **max_n_gram** *(type: int)* `[Optional, default = 6]`
    - Maximum length of the character N-grams

    * **threads** *(type: int)* `[Optional, default = 1]`
    - The number of threads to use

    * **sampling_threshold** *(type: double)* `[Optional, default = 0.0001]`
    - The sampling threshold

    * **label_prefix** *(type: string)* `[Optional, default = "__label__"]`
    - The prefix used for labels

    * **pretrained_vectors_file** *(type: string)* `[Optional, default = ""]`
    - The filename that contains the pretrained word vectors, if any. Leave blank for none

