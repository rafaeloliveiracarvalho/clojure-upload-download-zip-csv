FROM clojure:lein-alpine

WORKDIR /app

# Copy project.clj and install dependencies
COPY project.clj /app/
RUN lein deps

# Copy source code
COPY . /app

EXPOSE 3000

CMD ["lein", "run"]
