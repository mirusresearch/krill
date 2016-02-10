# Welcome to krill docs

## Concepts and Terminology

- "event owner" - This is likely going to be the name of the client application (your app).

## Register an event owner

Before you can add any events, you must register your event owner.  To do so, post to `http://krill.example.com/register/<owner-name>/` like so:

    curl -X POST http://krill.example.com/register/myapp/

## Add events

Post json to `http://krill.example.com/<owner-name>/`.  Here are the fields to post:

- id - optional (if you choose to leave it off, krill will provide a UUID) - This must be unique for the event owner
- timestamp - optional (if you choose to leave it off, krill will set it to current) - check [here](http://www.joda.org/joda-time/apidocs/org/joda/time/format/ISODateTimeFormat.html#basicDateTime--) for format info.  Here's a few examples (fairly sure I got the DST stuff right):
    - `2015-08-14T21:25:00.000Z` - 3:25pm Central Daylight Savings represented as UTC
    - `2015-08-14T15:25:00.000-05` - 3:25pm Central Daylight Savings
    - `2015-08-14T15:25:00.000-06` - 3:25pm Central Non-Daylight-Savings
- document - required - accepts any valid json
- category - required - just a string

### Response would look like so in the case of adding 3 events with 1 error

    {
        "error-count": 1,
        "results": ["<event-id-1>", "<event-id-2>", {"error": "Failure because oops"}]
    }

### curl example:

    curl -H "Content-Type: application/json" -X POST -d '
    {
        "events": [
            {
                "id": "i-know-this-is-unique",
                "timestamp": "2015-08-14T08:42:00.000Z",
                "document": {
                    "important_event_property": "xyz",
                    "clojure_rocks": true
                },
                "category": "event-of-your-dreams"
            },
            {...another event}
        ]
    }' http://krill.example.com/myapp/

## Query events

Here's how to query the events table.  Just POST the postgreql query as text data as shown in the example below.  krill is just gonna pass on the SQL and return the results as json.

    curl -H "Content-Type: application/text" -X POST -d "SELECT * FROM event ORDER BY created DESC LIMIT 25;" http://krill.example.com/q/myapp/
