# Use a base with both Node and Java
FROM node:18-alpine

# Install OpenJDK 17 (required by shadow-cljs)
RUN apk add --no-cache openjdk17

WORKDIR /app

# Copy package.json and install Node dependencies
COPY package.json package-lock.json /app/
RUN npm install && npm install -g serve

# Copy source and configurations
COPY . /app

# Build the project
RUN npx shadow-cljs release app

# Serve the 'public' directory on port 3001
EXPOSE 3001
CMD ["serve", "-s", "public", "-l", "3001"]
