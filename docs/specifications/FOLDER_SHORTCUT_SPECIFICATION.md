# NDEx API Update for Version 3.0


## Folder and Shortcut API

### Key Concepts and Specifications

#### General Behavior
- Networks and folders are created in the owner's **Home directory** by default, which is inherently non-shareable.
- When a user publishes their account, their networks, shortcuts, and folders become visible on their account page based on permissions:
  - **Anonymous users**: Can view only public items.
  - **Authenticated users**: Can view public items and any items for which they have been granted at least read permission.

---

### Definitions

#### **Files**
- Can be of type **Folder**, **Shortcut**, or **Network**.
- Has only one owner.
- Has a pointer to a parent object.

#### **Folder**
- Other objects can set a Folder as their parent object.
- **Home Folder**:
  - A special Folder object denoting the base/root folder for each owner.
  - Serves as the "Channel" Folder for the owner.

#### **Shortcut**
- An object that targets a single other Folder or Network File.
- Inherits permission of the target object. Any permission applied to the Shortcut is applied to the target.
- A Shortcut can become "dangling" if its target is removed.

#### **Network**
- An object that represents a graph.

---

### Definitions of Visibility

- **public**: Any user can view. Applies to Network and Folder. Public files are searchable.
- **unlisted**: Any user with the link can view. Files are only searchable by the owner.
- **private**: Only the owner can view/access/write Network or Folder Files.
  - Access keys can be assigned to specific Network Files for read access.
  - Owners can explicitly grant read or write access to others.
  - Only searchable by owner or users with at least read access.

---

### Definition of Permissions

#### **read**
- Applies to private visibility Files.
- Automatically applied to public/unlisted visibility Files.
- Implications:
  - **Folder**: Ability to see all Folders and Network Files with read/write access.
  - **Shortcut**: Visibility depends on the read/write permission of its target.
  - **Network**: Ability to view the network from Shortcuts and within Folder Files.

#### **write**
- Implications:
  - **Folder**: Includes all read permissions plus:
    - Rename Folders and Shortcuts.
    - Add new Shortcuts and Folders.
  - **Shortcut**: Permissions cannot be set directly; they inherit target permissions.
  - **Network**: Includes all read permissions plus:
    - Edit but not delete the network completely.
    - Rename the network.

---

### Limits
- **Folder-item limit**: 100,000 items per folder (Folders, Networks, and Shortcuts are counted).
- **Folder-depth limit**: Maximum of 20 levels of nested folders.

---

### Sharing Rules
- Only the owner can share their folders.
- The owner's **Home Folder** serves as their channel for exposing Networks and Folders. Its permissions cannot be changed.
- **Permission Propagation**:
  - Permissions propagate downward through nested Folders and Networks.
  - Moving a Folder to another parent Folder updates permissions recursively.

---

### Migration Rules

#### Network Sets
- Converted to Folder objects with read permission and a Shortcut pointing to each Network added.

#### Groups
- Converted into Folders owned by the Group owner.
- Shortcuts to Networks are added to the new Folder.

#### Networks
- Remain in the Home Folder with permissions mapped from old values to new ones.

---

### New Functions for Folders and Shortcuts

#### **Move Networks**
```http
POST /v3/batch/networks/move
```plaintext
**Request Body**:
```json
{
   "target_folder": "<uuid>",
   "networks": ["<uuids>"]
}
```plaintext

#### **Set Visibility on File**
```http
POST /v3/batch/files/setvisibility
```plaintext
**Request Body**:
```json
{
   "visibility": "<Public|Private|Unlisted>",
   "files": [
      { "uuid": "<uuid>", "type": "<network|folder|shortcut>" },
      ...
   ]
}
```plaintext

#### **Create a Folder**
```http
POST /v3/files/folders
```plaintext
**Request Body**:
```json
{
   "name": "<string>",
   "parent": "<uuid>"
}
```plaintext
**Response**:
- Header: `location` contains the URL of the folder.
- Body:
```json
{
   "uuid": "<uuid>",
   "lastModified": "<long>"
}
```plaintext

#### **Create a Shortcut**
```http
POST /v3/files/shortcuts
```plaintext
**Request Body**:
```json
{
   "name": "<string>",
   "parent": "<uuid>",
   "target": "<uuid>"
}
```plaintext

#### **Delete a Folder**
```http
DELETE /v3/files/folders/<UUID>?permanent=<true|false>&force=<true|false>
```plaintext
- Default:
  - `permanent=false` (logical delete).
  - `force=false` (only delete if empty).

#### **Delete a Shortcut**
```http
DELETE /v3/files/shortcuts/<UUID>?permanent=<true|false>
```plaintext

#### **Restore Objects**
```http
POST /v3/files/trash/restore
```plaintext
**Request Body**:
```json
{
   "networks": ["<network_uuids>"],
   "folders": ["<folder_uuids>"],
   "shortcuts": ["<shortcut_uuids>"]
}
```plaintext

---

### Additional API Functions
- **Move/Rename Folder**: `PUT /v3/files/folders/<UUID>`
- **Copy Network/Folder**: `POST /v3/files/copy`
- **List Objects in Folder**: `GET /v3/files/folders/<uuid>/list?format=<update|compact>`
- **Get Object Count in Folder**: `GET /v3/files/folders/<uuid>/count`

---

### Database Updates for Version 3.0

#### New Tables
- `core.folder`
- `core.shortcut`
- `core.folder_permission`

#### Schema Updates
- Added `parent` and `show_in_trash` columns to `core.network`.
- Indexed `show_in_trash` in `core.network`.

---

### Notes
- Logical deletion retains objects for 30 days in the trash bin.
- Users can recover objects within this retention period.
- Trash bin objects count toward storage limits.

---
