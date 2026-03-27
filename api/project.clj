(defproject api "0.1.0-SNAPSHOT"
  :description "API for File Upload/Download"
  :url "https://example.com"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.12.2"]
                 [metosin/reitit "0.7.0"]
                 [metosin/muuntaja "0.6.10"]
                 [ring/ring-core "1.11.0"]
                 [ring/ring-jetty-adapter "1.11.0"]
                 [ring/ring-defaults "0.5.0"]
                 [ring-cors "0.1.13"]
                 ;; S3
                 [software.amazon.awssdk/s3 "2.25.10"]
                 [software.amazon.awssdk/auth "2.25.10"]
                 ;; Database
                 [com.github.seancorfield/next.jdbc "1.3.939"]
                 [org.postgresql/postgresql "42.7.3"]
                 [com.taoensso/timbre "6.1.0"]]

  :main ^:skip-aot clojure-download-zip-csv.core
  :target-path "target/%s"
  :source-paths ["src"]
  :resource-paths ["resources"]
  :profiles {:uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}})
