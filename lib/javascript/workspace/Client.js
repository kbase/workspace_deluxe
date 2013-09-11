

function Workspace(url, auth, auth_cb) {

    var _url = url;
    var deprecationWarningSent = false;
    
    function deprecationWarning() {
        if (!deprecationWarningSent) {
            deprecationWarningSent = true;
            if (!window.console) return;
            console.log(
                "DEPRECATION WARNING: '*_async' method names will be removed",
                "in a future version. Please use the identical methods without",
                "the'_async' suffix.");
        }
    }

    if (typeof(_url) != "string" || _url.length == 0) {
        _url = "http://kbase.us/services/workspace/";
    }
    var _auth = auth ? auth : { 'token' : '', 'user_id' : ''};
    var _auth_cb = auth_cb;


    this.create_workspace = function (params, _callback, _errorCallback) {
    return json_call_ajax("Workspace.create_workspace",
        [params], 1, _callback, _errorCallback);
};

    this.create_workspace_async = function (params, _callback, _error_callback) {
        deprecationWarning();
        return json_call_ajax("Workspace.create_workspace", [params], 1, _callback, _error_callback);
    };

    this.get_workspace_metadata = function (wsi, _callback, _errorCallback) {
    return json_call_ajax("Workspace.get_workspace_metadata",
        [wsi], 1, _callback, _errorCallback);
};

    this.get_workspace_metadata_async = function (wsi, _callback, _error_callback) {
        deprecationWarning();
        return json_call_ajax("Workspace.get_workspace_metadata", [wsi], 1, _callback, _error_callback);
    };

    this.get_workspace_description = function (wsi, _callback, _errorCallback) {
    return json_call_ajax("Workspace.get_workspace_description",
        [wsi], 1, _callback, _errorCallback);
};

    this.get_workspace_description_async = function (wsi, _callback, _error_callback) {
        deprecationWarning();
        return json_call_ajax("Workspace.get_workspace_description", [wsi], 1, _callback, _error_callback);
    };

    this.set_permissions = function (params, _callback, _errorCallback) {
    return json_call_ajax("Workspace.set_permissions",
        [params], 0, _callback, _errorCallback);
};

    this.set_permissions_async = function (params, _callback, _error_callback) {
        deprecationWarning();
        return json_call_ajax("Workspace.set_permissions", [params], 0, _callback, _error_callback);
    };

    this.get_permissions = function (wsi, _callback, _errorCallback) {
    return json_call_ajax("Workspace.get_permissions",
        [wsi], 1, _callback, _errorCallback);
};

    this.get_permissions_async = function (wsi, _callback, _error_callback) {
        deprecationWarning();
        return json_call_ajax("Workspace.get_permissions", [wsi], 1, _callback, _error_callback);
    };

    this.save_objects = function (params, _callback, _errorCallback) {
    return json_call_ajax("Workspace.save_objects",
        [params], 1, _callback, _errorCallback);
};

    this.save_objects_async = function (params, _callback, _error_callback) {
        deprecationWarning();
        return json_call_ajax("Workspace.save_objects", [params], 1, _callback, _error_callback);
    };

    this.get_objects = function (objects, _callback, _errorCallback) {
    return json_call_ajax("Workspace.get_objects",
        [objects], 1, _callback, _errorCallback);
};

    this.get_objects_async = function (objects, _callback, _error_callback) {
        deprecationWarning();
        return json_call_ajax("Workspace.get_objects", [objects], 1, _callback, _error_callback);
    };
 

    /*
     * JSON call using jQuery method.
     */
    function json_call_ajax(method, params, numRets, callback, errorCallback) {
        var deferred = $.Deferred();

        if (typeof callback === 'function') {
           deferred.done(callback);
        }

        if (typeof errorCallback === 'function') {
           deferred.fail(errorCallback);
        }

        var rpc = {
            params : params,
            method : method,
            version: "1.1",
            id: String(Math.random()).slice(2),
        };
        
        var beforeSend = null;
        var token = (_auth_cb && typeof _auth_cb === 'function') ? _auth_cb()
            : (_auth.token ? _auth.token : null);
        if (token != null) {
            beforeSend = function (xhr) {
                xhr.setRequestHeader("Authorization", token);
            }
        }

        jQuery.ajax({
            url: _url,
            dataType: "text",
            type: 'POST',
            processData: false,
            data: JSON.stringify(rpc),
            beforeSend: beforeSend,
            success: function (data, status, xhr) {
                var result;
                try {
                    var resp = JSON.parse(data);
                    result = (numRets === 1 ? resp.result[0] : resp.result);
                } catch (err) {
                    deferred.reject({
                        status: 503,
                        error: err,
                        url: _url,
                        resp: data
                    });
                    return;
                }
                deferred.resolve(result);
            },
            error: function (xhr, textStatus, errorThrown) {
                var error;
                if (xhr.responseText) {
                    try {
                        var resp = JSON.parse(xhr.responseText);
                        error = resp.error;
                    } catch (err) { // Not JSON
                        error = "Unknown error - " + xhr.responseText;
                    }
                } else {
                    error = "Unknown Error";
                }
                deferred.reject({
                    status: 500,
                    error: error
                });
            }
        });
        return deferred.promise();
    }
}


