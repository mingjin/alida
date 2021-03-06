.. image:: https://secure.travis-ci.org/fmw/alida.png

================================================================
Alida: a crawling, scraping and indexing tool written in Clojure
================================================================

**A video for the talk is up at: http://vimeo.com/45132055/**

The Alida project was started as companion code to my talk at
`EuroClojure 2012`_ on the topic of "Building a search engine with
Clojure". The goal of this application is to provide the back-end for
a simple search engine, while the front-end (i.e. the part that
performs the actual searches and displays the results to the visitor)
is separate. Alida provides the following functionality:

1. Data retrieval (web crawling).
2. Storage (storing the crawled pages).
3. Scraping (extracting interesting bits and pieces from documents).
4. Indexing (storing the end result in a searchable index).

The application depends on several external libraries and
applications, including `Apache CouchDB`_ to store the crawled pages,
`Apache Lucene`_ for indexing, `clj-http`_ as a wrapper around the
`Apache HttpClient`_, `Clutch`_ as a CouchDB library and both
`Enlive`_ and `Jsoup`_ for scraping data from the retrieved
documents. As an experimental project Alida isn't designed for
immediate production use. Some technology choices reflect that. Apache
CouchDB, for example, isn't the most obvious choice for storing
crawled pages. For large scale document storage something like `HDFS`_
would be more efficient, but CouchDB is easier to set up in a small,
experimental setting. If you need something more mature, I'd suggest
looking at `Apache Nutch`_, which also includes a web crawler. That
being said, one of the goals for Alida is to be able to power a
real-world search engine project.

Check the docs/ directory for the slides. I've also created two
sub-projects. `clojure-blog-search`_ is a very basic example of how
you could use Alida to crawl the web. Please remember that it does an
actual crawl on the personal blogs of fellow Clojurians, so please
don't run it unless you have a very good reason to do so. I've also
created `clojure-blog-search-www`_, which is a little front-end web
application that also includes a pre-built Lucene index that you can
experiment with. If you are less inclined towards diving straight into
the code and want to see a hosted example you can find one at
http://clojure-blog-search.vixu.com/.

You can use this code as a library in your own project by adding the
following to the :dependencies in your project.clj file::

    [alida "0.1.3"]


Questions?
----------

Don't hesitate to contact me if you have any questions or
feedback. You can email me at fmw@vixu.com.

About me
--------

My name is Filip de Waard. As the founder of `Vixu.com`_ I write
Clojure code for a living. The main focus of `Vixu.com`_ is providing
website-management software as a service. Under the hood we use the
free, open source `Vix`_ application to power the service. My company
is also working on a product search application written in Clojure.


License
-------

Copyright 2012, F.M. de Waard / `Vixu.com`_.
All code is covered by the `Apache License, version 2.0`_.

.. _`clojure-blog-search`: https://github.com/fmw/clojure-blog-search
.. _`clojure-blog-search-www`: https://github.com/fmw/clojure-blog-search-www
.. _`EuroClojure 2012`: http://euroclojure.com/2012/
.. _`Apache CouchDB`: http://couchdb.apache.org/
.. _`Apache Lucene`: http://lucene.apache.org/core/
.. _`clj-http`: https://github.com/dakrone/clj-http
.. _`Apache HttpClient`: http://hc.apache.org/httpcomponents-client-ga/index.html
.. _`Clutch`: https://github.com/clojure-clutch/clutch
.. _`Enlive`: https://github.com/cgrand/enlive
.. _`Jsoup`: http://jsoup.org/
.. _`HDFS`: http://hadoop.apache.org/hdfs/
.. _`Apache Nutch`: http://nutch.apache.org/
.. _`Vixu.com`: http://www.vixu.com/
.. _`Vix`: https://github.com/fmw/vix
.. _`Apache License, version 2.0`: http://www.apache.org/licenses/LICENSE-2.0.html
