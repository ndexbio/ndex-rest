# NDEx API Update for Version 3.0


# Folder and shortcut API

## Some key concepts and specs:

Here's a concise edit using a more scientific writing style:

Networks and folders are, by default, created in the owner's Home directory, which is inherently non-shareable. When a user publishes their account, their networks, shortcuts, and folders become visible on their account page based on set permissions. Anonymous users can view only public items on the account page. Authenticated users can see public items and any items for which they have been granted at least read permission.

### Definitions:

* **Files**  
  * Can be of type Folder, Shortcut, or Network  
  * Has only one owner  
  * Has a pointer to parent object  
  *   
* **Folder**  
  * Other objects can set a Folder as their parent object  
  * Home Folder  
    * Special Folder object denoting base/root folder for each owner  
    * It is the “Channel” Folder for the owner  
* **Shortcut**  
  * An object that targets a single other Folder or Network File  
  * Inherits permission of what the Shortcut targets and any permission applied to Shortcut is actually applied to what it targets  
  * It is possible for target of a Shortcut to be removed resulting in a dangling Shortcut  
* **Network**  
  * An object that represents a graph.  
  


### Definitions of Visibility:

* **PUBLIC**  
  * Means any user can view. Applies to Network and Folder.   
  * Files that are public are searchable.  
* **UNLISTED**  
  * Any user with the  link can view. Applies to Network and Folder.  
  * Files are only searchable by the owner.  
* **PRIVATE**   
  * Means only the owner can view/access/write Network or Folder Files.  
    * For others  
      * Access key can be given/assigned to a specific Network File giving whoever has the key and knows the Network File `read` access  
      * Owner has explicitly given `read` or `write` access to Network or Folder Files.  
        

### Definition of permissions: 

* `read` (can be applied to private visibility Files)
  * For `PUBLIC`/`UNLISTED` visibility Files, read is automatically applied  
    * What does mean for these entities:  
      * **Folder**  
        * Ability to see all Folders & Network Files that have `read` or `write` access  
          * Shortcut File visibility depends on read/write permission of Shortcut target  
      * **Shortcut**  
        * Inherits whatever the Shortcut targets  
      * **Network**  
        * Ability to see network from Shortcut and within Folder Files  
  * `write`  
    * What does mean for these entities:  
      * **Folder**  
        * All read permissions \+  
          * Can rename Folders and Shortcuts  
            * Can add new Shortcuts and Folders  
      * **Shortcut**  
        * It is not possible to set permission on Shortcuts. They inherit target permissions  
      * **Network**  
        * All read permissions \+  
          * Can edit, but not delete network completely  
          * Can rename
    
* Youtube  model for permissions:  
  [https://support.google.com/youtube/answer/157177?sjid=8700513507205805673-NC](https://support.google.com/youtube/answer/157177?sjid=8700513507205805673-NC)  
  

### Limits:

Folder-item limit: Each folder has a limit of `100,000` items. Folders, Networks and Shortcuts are counted toward this limit.

Folder-depth limit: A folder can’t contain more than `20` levels of nested folders.

### Listing a folder's contents:

`GET /v3/files/folders/{folderid}/list` takes `start` (zero-based row offset, default `0`) and `size`
(page size). Because a folder-item limit of 100,000 is far more than any client wants in one response,
these let a caller walk a large folder a page at a time.

* **The default is unbounded.** Omitting `size` returns every item, so a call written before pagination
existed behaves exactly as it did. Pass `-1`, or any non-positive value, to ask for all items
explicitly.
* Items are ordered by last modification time descending, nulls last, tie-broken by UUID. That order is
total and stable, so concatenating consecutive pages reproduces one unbounded listing — without the
tiebreaker, rows sharing a modification time could repeat or vanish between pages.
* A `start` past the last item is an empty array with `200`, not an error. A negative `start` is `400`.
* The response is a bare array and carries no total. `GET /v3/files/folders/{folderid}/count` gives the
counts under the same visibility rules. The two are not directly comparable under a `type` filter,
since `/list?type=network` also returns network-targeted shortcuts while `FileCount.network` does not
count them.

### Sharing:

* Only the owner can share their folders.   
* The owner’s channel for exposing Networks and Folders is the owner’s Home Folder. The Home folder permissions cannot be changed.  
* Default folder permission propagation rules (endpoints need parameters to alter)  
  * Permission lists for a folder propagate downward. Networks and Folders inherit permissions from the parent.   
  * Whenever permissions of a Folder are changed, the new permission propagates recursively through all nested Folders and Networks.   
    * For example, if a Network exists in a Folder and that Folder is then moved within another Folder, the permissions on the moved Folder propagate to the Network. If the moved Folder grants the user the Network a new permission, such as `write`, it overrides their old permission.   
    * Conversely, if a Network inherits `write` from a Folder, and is moved to another Folder that provides a read permission, the Network now inherits `read permission`.

### Access key propagation through folders

Access keys are a separate mechanism from per-user `read`/`write` permissions: an access key is an
anonymous, **binary READ grant** (there is no write-granting access key), issued via
`POST /v3/sharing/share`. Access keys follow the **same downward propagation as folder permissions**:

* A folder's access key grants anonymous `read` to **every Network and Folder nested beneath it**,
  through the entire subtree.
* Validity accrues over the whole ancestor chain (**OR semantics**): a key is valid for a Network or
  Folder if it matches the enabled key of that item's own record (for a Network, its own access key)
  or of **any** ancestor Folder, walking `parent` up to the root. There is no "nearest parent
  overrides" rule — because a key is only ever `read`, there is nothing to override.
* **Shortcut traversal (same-owner NETWORK carve-out):** in general an access key does **not**
  propagate through a Shortcut to the Shortcut's target — matching how permissions treat Shortcuts (a
  Shortcut inherits its *target's* permission, not its container's) and the Google Drive model, where
  folder sharing does not flow through a shortcut to a file elsewhere. **Exception (backwards
  compatibility, issue #133/#137):** for a **Network** target, a key **is** valid when a *same-owner*
  (Shortcut owner = target Network owner) live `NETWORK` Shortcut pointing at it resides in a Folder whose
  ancestry carries the matching enabled key. This restores access keys stranded by the v3 networkset
  migration, which converted keyed networksets into keyed Folders full of Shortcuts to member Networks
  that stayed in their owner's Home folder. The same-owner guard prevents a keyed Folder from granting
  anonymous read to a Network its owner does not own. `FOLDER`-target Shortcuts are still not traversed.
* Consistent with the above, `GET /v3/files/folders/{folderid}/list` and `/count`, when a request
  **presents a valid access key**, return the key-accessible children — folders, networks, and
  **same-owner `NETWORK` Shortcuts** whose target networks the key now unlocks (other Shortcut children
  are excluded). When a request provides a valid access key, this key-accessible result is returned
  regardless of the caller's identity; when no valid key is provided, the caller gets their
  identity-based view (owners and users with direct `read`/`write` see the full listing, including all
  Shortcuts).

This behavior is implemented once in `AccessKeyResolver`
(`org.ndexbio.common.models.dao.AccessKeyResolver`), shared by the network and folder DAOs.

### Migration rules

* **Network Sets**  
  * Every network set will be converted to a Folder object with read permission and a shortcut pointing to each Network added  
* **Remove the users search page from UI and service?**  
  * User has to opt-in to be searchable in his profile.  
* **Networks**  
  * All Networks stay in Home folder, permissions map from old values to new values  
  * Access keys also get migrated   
* **Groups**  
  * Permissions are delegated onto Networks for each owner.  
  * Group is turned into Folder for Owner of Group.   
  * Shortcuts to Networks are put in Group.  
* **Showcase**  
  * Is ignored, has no relevance

### These are new functions we are going to add:

## New functions for Folders and shortcuts

In the API, we will add a new **files** endpoint in NDEx to handle folders and shortcuts. In the future we can extend this endpoint to handle other file types if needed.

* Move Networks `POST /v3/batch/networks/move`

  Body: 

  ```
  {  
     “target\_folder”: <uuid>,  
      “networks”: [ <uuids>]

  }
  ```  
    
* Set visibility on file `POST /v3/batch/files/setvisibility`

  Body:

  ```  
		{  visibility: <Public|Private|Unlisted>,  
			[ {uuid: x,  
			   type: <network|folder|shortcut>  
			}, {...}]  
		}
   ```
  
* Create a Folder `POST /v3/files/folders`
  
  Request Body fields: 


  | name | string Name of the folder. |
  | :---- | :---- |
  | parent | string The UUID of the parent folder that contains this folder. If the value is missing or null, the object will be created in user’s home directory. |

  Response:

  Header attribute location should have the url of the folder. 

  Return code 201

  Body:

    ```
    { uuid: <uuid>,
       lastModified: long //creation time
    }
	```

* Create a shortcut  `POST /v3/files/shortcuts`

  //renamed from create_shortcut  
  Request Body fields: 

  | name | string Name of the folder or shortcut. |
  | :---- | :---- |
  | parent | string The ID of the parent folder that contains this object. If the value is missing or null, the object will be created in user’s home folder. |
  | target | String UUID of the target. The target can be either a network, folder, or a shortcut.  |

  Response:

  Header attribute `location` should have the url of the folder. 

  Return code 201

  Response Body:

  ```
  { uuid: <uuid>,

    lastModified: long //creation time

  }
  ```


* Delete a folder `DELETE /v3/files/folders/<UUID>?permanent=<true|force>&force=<true|false>`
  
  Default value for permanent is false which means logical delete  
  Default value for force is false, which means the folder can only be deleted if it is empty. When force is true all networks, shortcuts and
  subfolders in the folder will be deleted.   
  Response: 204 No Content   
    
* Delete a shortcut `DELETE /v3/files/shortcuts/<UUID>?permanent=<true|force>`
  
  Default value for permanent is false which means logical delete  
  Response: 204 No Content   
    
* Restore objects  `POST /v3/files/trash/restore`
  
  Body:

  ```
    {  
        “networks” : [ network_uuids ]  
        “folders”: [folder_uuids]  
        “shortcuts” : [shortcut_uuids] 

    }
  ```

* Move or rename a folder `PUT /v3/files/folders/<UUID>`
	  
  Body:

  ```
  {  
		“name”: <string> // new name  
		“new_parent”: <uuid>  //UUID of the parent directory
  }
  ```

* Copy a network or folder `POST /v3/files/copy`

  Body:

  ```
  {  
     “from_uuid” : <uuid>  
     “type” : <network|folder>   
     “to_path” : <uuid>  // copy to users home directory if this attribute is missing  
  }
  ```

* Move or rename a shortcut `PUT /v3/files/shortcuts/<UUID>`  

  Body:

  ```
  {  
	 “name”: <string> // new name  
  }
  ```

* Get a folder object `GET /v3/files/folders/<uuid>`
    
  Body:

  ```   
  {   
     “name”: <string>
     “uuid”: <uuid>
     “parent” : <uuid>
     “creationTime” : <long>
     “lastModificationTime” : <long>  
     “is_deleted” : <boolean>  
  }
  ```

*  Get a shortcut object `GET /v3/files/shortcuts/<uuid>`
  
   Body:

   ```   
   {   
       “name”: <string>
       “uuid”: <uuid>  
       “parent” : <uuid>  
       “target” : <uuid>  
       “creationTime” : <long>   
       “lastModificationTime” : <long>  
       “Is_deleted” : <boolean>  
   }
   ```

* List objects in a folder `GET /v3/files/folders/<uuid>/list?format=<update|compact>`

  Accepts the literal `home` in place of a uuid for the caller's top level, and `start`/`size`
  for paging. Despite the names, `compact` is the **fuller** view — it adds description,
  visibility, folder creationTime and network edgecount — while `update` (the default) omits them.

  Response:

  ```  
  [  
    {  
		“uuid” : <uuid>  
        “type” : <folder|network|shortcut>  
        “name” : <string>   
    }  
		…
  ]
  ```	

* Get the object count in a folder `GET /v3/files/folders/<uuid>/count`

  Response:

  ```
  {  
      “network : <integer>  
      “folders” : <integer>  
      “shortcuts” : <integer>	  
  }
  ```

* Adds/Update/Deletes permission  `POST /v3/sharing/members`
   
  Body:  
  ```     
  {  
     “files” : <uuid>, <network|folder>  
     “member”: {   
                  <user_uuid>: <WRITE|READ> or null  
                  …        
               }  
  }
  ```

  Member attribute contains the members whose access are changed.

* Share an object `POST /v3/sharing/share  `

  Creates an access key that can be used to generate a shareable URL, which grants READ permission to anonymous users.
  For Shortcuts only works for Shortcuts pointing to things the owner owns.   
  
  Body:
  ```
       {  
         “uuid” : <uuid>  
         “type” :  <network|folder>	  
        }
  ```
  
  Response: accessKey

           

* unshare an object `POST /v3/sharing/unshare`
  
  Body:
  ``` 
       {  
         “uuid” : <uuid>  
         “type” :  <network|folder>	  
        }
  ```
   
  Response: return code 204

* transfer an object `POST /v3/sharing/transfer`
  
  Body:
  ```
       {  
         “files” : <uuid>,  <network|folder>  
          “new_owner”: <new_owner>  	  
        }
  ``` 
          
  Response: return code 204  
    
* Get my object count `GET /v3/files/count`
    
  Response:

  ```  
  {  
      “network”:  <long>  //my network count  
      “folder”:    <long>   
      “shortcut” : <long>  
  }
  ```

* List all shared objects  `POST /v3/sharing/list?limit=100`

  Where shared objects are folders and networks that the current user has READ or WRITE permission on, they should not be owned by current user
    
  Response:

  ```  
  [  
  	{	  
  	   “uuid”: <uuid>  
       “type”: <network|folder>  
  	   “owner”: string  
  	   “owneruuid”: <uuid>  
       “object” : <FileItemSummary>  	  
     }

  ]
  ```  
    
*Removed in 3.0.7 (issue #163): `GET /v3/files/folders` and `GET /v3/files/shortcuts`.* Both listed
everything the caller owned at any depth with a `limit` but no pagination. Use the folder listing
above instead, starting at the `home` literal and recursing per folder for depth:

* Your folders: `GET /v3/files/folders/home/list?type=folder`, keeping items whose `type` is `folder`
  — folder-targeted shortcuts are returned alongside them.
* Your shortcuts: `GET /v3/files/folders/home/list` with **no** `type` filter, keeping items whose
  `type` is `shortcut`. Note `?type=shortcut` returns an **empty array by design**: the filter selects
  a shortcut's *target*, and a target is only ever a folder or a network.

## New and updated API functions for network 

**V3**  

**POST** `/v3/networks`  
Add query parameter named `folderid` which denotes parent folder for network

**GET** `/v3/networks/{networkid}/summary`  
Returned json should include `folderid in result and showintrash`

**GET** `/v3/users/{userid}/home`  
For anonymous use this gets all folders, networks, shortcuts that are public for the given user in their home folder. If the user is signed, then this should include all the folders, networks, shortcuts shared with the signed in user. If a user is signed in and the userid matches the user, then show everything for that user as done with the endpoint ???

**POST** `/v3/search/files`  
Returns folders, networks, and shortcuts that match the query

**V2**  

**POST** `/v2/search/network`  
Returned json should include folderid in result and showintrash

**POST** `/v2/search/network/genes`  
Returned json should include folderid in result and showintrash

**POST** `/v2/batch/network/summary`  
Returned json should include folderid in result and showintrash

**GET** `/v2/user/{userid}/networksummary`  
Returned json should include folderid in result and showintrash

**GET** `/v2/user/{userid}/showcase`  
Returned json should include folderid in result and showintrash  
Should only return networks that are in home folder and public. Also deprecate this call

**PUT** `/v2/network/{networkid}/systemproperty`  
Showcase should be a noop and note it in the documentation

**POST** `/v2/search/group`  
Returns folder  
`/v2/request`   
Mark endpoints as deprecated in code and in swagger documentation

**GET** `/v2/user/{userid}/showcase`  
List as deprecated in code and in swagger, but return same thing as **GET** `/v3/folders/{userid}/home`

**GET** `/v2/user/{userid}/networksummary`  
List as deprecated in code and in swagger, but return same thing as **GET** `/v3/users/{userid}/home`

**POST** `/v2/search/network`
**POST** `/v2/search/network/genes`  
Returned json should include folderid in result and showintrash

Networksets endpoints should be deprecated in code and in swagger — but kept working, folder-backed. See
[Networkset](#networkset) under Migration path for the implemented behavior.

**POST** `/v2/batch/group`  
Return information about folders posted

|  |  |
| :---- | :---- |
|  |  |

# Migration path

## Group

Each group should be turned into a folder and the owner of the folder is the owner of the group with any networks added as shortcuts into that folder. Permissions on networks must be propagated over to the new folder and the networks if not already containing the permission.

## Networkset

Each networkset is converted into a folder owned by the networkset owner and networks are placed as shortcuts in that folder. The networkset id should be used as the folder id.   
The networkset endpoints should be deprecated in swagger, but if used the networksetid is equivalent to the folderid and will still continue to work.

**Status: implemented.** All `/v2/networkset` endpoints (plus `GET /v2/user/{userid}/networksets` and the
`networkSetCount` field of `GET /v2/user/{userid}/networkcount`) operate on folders and shortcuts and map
the result back onto the legacy `NetworkSet` representation; the `network_set` tables are never read or
written. Three legacy fields have no folder equivalent and are not round-tripped — `showcased`, `doi`
and `properties` — so `PUT /{networksetid}/systemproperty` accepts `showcase` as a documented no-op,
matching the ruling for `PUT /v2/network/{networkid}/systemproperty` above. See the
[V3 Migration Guide](../V3-Migration-Guide.md) §3 for the full per-endpoint mapping and the two places
where behavior is deliberately narrower (access keys reach only same-owner member networks; adding
members validates read access on every posted id).

# Other Changes

V3 endpoints that returns cx2 data should have content type json+cx2

Hide v2/network provenance, samples from swagger and in case add deprecated to swagger docs

### Logically Delete a network

The logically deleted objects will be retained for 30 days in the trash bin. Users can recover the network with the retention period. System will automatically remove the network after the retention period. Logically deleted networks still count towards the user's storage limit. Additional parameter permanent=true will force an immediate physical deletion.

Trash bin is located in this resource category: files/trash

API functions:

List all objects in trash bin

**GET** `/v3/files/trash`

Permanently deletes all trashed objects:  
**DELETE** `/v3/files/trash`

Delete network function in v3:
**DELETE** `/v3/networks/<UUID>?permanent=<false|true>`

Parameter permanent defaults to false which means a logical delete.

     
Notes:  
	Google allows dangling shortcuts. This is the UI:  
![][image1]  
We will support it.  
            If a folder is not readable, but the network is visible, can users see the network?  
	In Google Drive, users can share a file under a private folder and make it editable to anyone who has the link. Similarly, a network can be editable to other users in a private folder.  
           

## Database updates for 3.0

A few new tables for folders and shortcuts. Folder objects and shortcut objects will be stored in corresponding tables:

Changes are available on dev1 server now.

```
CREATE TABLE core.folder  
(  
    "UUID" uuid NOT NULL,  
    creation_time timestamp without time zone NOT NULL,  
    modification_time timestamp without time zone NOT NULL,  
    is_deleted boolean NOT NULL,  
    name character varying(300) NOT NULL,  
    visibility character varying(100) NOT NULL,  
    owneruuid uuid NOT NULL,  
    access_key character varying(500),  
    access_key_is_on boolean NOT NULL,  
    updated_by uuid,  
    solr_indexed boolean,  
    show_in_trash boolean DEFAULT False,  
    parent uuid,  
    description character varying,  
    PRIMARY KEY ("UUID"),  
    FOREIGN KEY (owneruuid)  
        REFERENCES core.ndex_user ("UUID") MATCH SIMPLE  
        ON UPDATE NO ACTION  
        ON DELETE NO ACTION  
        NOT VALID,  
    FOREIGN KEY (updated_by)  
        REFERENCES core.ndex_user ("UUID") MATCH SIMPLE  
        ON UPDATE NO ACTION  
        ON DELETE NO ACTION  
        NOT VALID  
);

ALTER TABLE IF EXISTS core.network  
    ADD COLUMN IF NOT EXISTS parent uuid;

ALTER TABLE IF EXISTS core.network  
    ADD COLUMN show_in_trash boolean DEFAULT False;

COMMENT ON COLUMN core.network.show_in_trash  
    IS 'Indicates whether the object should appear in the trash bin.';

ALTER TABLE IF EXISTS core.network  
    ADD FOREIGN KEY (parent)  
    REFERENCES core.folder ("UUID") MATCH SIMPLE  
    ON UPDATE NO ACTION  
    ON DELETE NO ACTION  
    NOT VALID;

CREATE TABLE IF NOT EXISTS core.shortcut  
(  
    "UUID" uuid NOT NULL,  
    creation_time timestamp without time zone NOT NULL,  
    modification_time timestamp without time zone NOT NULL,  
    is_deleted boolean NOT NULL,  
    name character varying(300) COLLATE pg_catalog."default" NOT NULL,  
    target_type character varying(100) COLLATE pg_catalog."default",  
    target uuid,  
    visibility character varying(100) COLLATE pg_catalog."default",  
    owneruuid uuid,  
    updated\_by uuid,  
    solr_indexed boolean,  
    show_in_trash boolean DEFAULT false,  
    parent uuid,  
    CONSTRAINT shortcut_pkey PRIMARY KEY ("UUID"),  
    CONSTRAINT shortcut_owneruuid_fkey FOREIGN KEY (owneruuid)  
        REFERENCES core.ndex_user ("UUID") MATCH SIMPLE  
        ON UPDATE NO ACTION  
        ON DELETE NO ACTION,  
    CONSTRAINT shortcut_parent_fkey FOREIGN KEY (parent)  
        REFERENCES core.folder ("UUID") MATCH SIMPLE  
        ON UPDATE NO ACTION  
        ON DELETE NO ACTION  
)

CREATE TABLE core.folder_permission  
(  
    folder_id uuid NOT NULL,  
    user_id uuid NOT NULL,  
    permission character varying(100),  
    PRIMARY KEY (folder_id, user_id),  
    FOREIGN KEY (folder_id)  
        REFERENCES core.folder ("UUID") MATCH SIMPLE  
        ON UPDATE NO ACTION  
        ON DELETE NO ACTION  
        NOT VALID,  
    FOREIGN KEY (user_id)  
        REFERENCES core.ndex_user ("UUID") MATCH SIMPLE  
        ON UPDATE NO ACTION  
        ON DELETE NO ACTION  
        NOT VALID  
);

ALTER TABLE IF EXISTS core.folder_permission  
    OWNER to ndexserver;

CREATE INDEX idx_network_owneruuid_trash_true  
ON core.network (owneruuid)  
WHERE show_in_trash = true;
```
