import { Client } from "k6/net/grpc";

const client = new Client();
client.load(["definitions"], "helloworld.proto");
