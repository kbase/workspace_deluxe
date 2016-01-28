wss contained the 10 workspaces created by NamebyPrefix.java

Each workspace contained 100K objects with names ranging from aaaa to fryd.

    In 24: %timeit ws.get_names_by_prefix({'workspaces': wss, 'prefix': ''});
    100 loops, best of 3: 8.25 ms per loop

    In 25: %timeit ws.get_names_by_prefix({'workspaces': wss, 'prefix': 'a'});
    100 loops, best of 3: 9.48 ms per loop

    In 26: %timeit ws.get_names_by_prefix({'workspaces': wss, 'prefix': 'ab'});
    100 loops, best of 3: 9.66 ms per loop

    In 27: %timeit ws.get_names_by_prefix({'workspaces': wss, 'prefix': 'abc'});
    100 loops, best of 3: 5.29 ms per loop

    In 28: %timeit ws.get_names_by_prefix({'workspaces': wss, 'prefix': 'abcd'});
    100 loops, best of 3: 3.96 ms per loop


