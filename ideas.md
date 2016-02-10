# Ideas and Planning

## DB Structure

- database is per project (eg. mysfdomain, kaczynski)
  - Table - event
    - fields
      - id (optional; guid or user supplied)
      - created (not supplied; pg timestamp)
      - timestamp (optional; pg timestamp; time of the event - defaults to created if empty)
      - category (required, varchar 255)
      - document (required; jsonb)

## API Endpoint Ideas

- ES eg. es/el/agent/<event id>
- krill/query?q=foobar&start=2013-02-01&end=2014-03-01

### Bulk

bulk insert suggestion:

    curl -H "Content-Type: application/json" -X POST -d 
    '[
        {
            "id"        : "1",
            "category"  : "foo",
            "timestamp" : "2015-08-14T08:42:00????",
            "document"  : {
                "important_event_property":"xyz",
                "clojure_rocks": true
            }
        },
        {
            "id"        : "2",
            "category"  : "foo",
            "timestamp" : "2015-08-14T08:42:00????",
            "document"  : {
                "important_event_property":"xyz",
                "clojure_rocks": true
            }
        }
    ]'

    http://krill.mirusresearch.com/myapp/_bulk/

also could do something like `{"bulk": [...]}`
