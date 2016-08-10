/**
 * Copyright (c) 2013, 2016, The Regents of the University of California, The Cytoscape Consortium
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */
package org.ndexbio.rest.services;

import org.junit.Assert;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.object.Group;


@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class TestGroupService extends TestNdexService
{
    private static final GroupService _groupService = new GroupService(_mockRequest);
    
 /*   @Test
    public void createGroup()
    {
   //     Assert.assertTrue(createNewGroup());
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void createGroupInvalid() throws IllegalArgumentException, NdexException
    {
   //     _groupService.createGroup(null);
    }
   /* 
    @Test(expected = IllegalArgumentException.class)
    public void createGroupInvalidName() throws IllegalArgumentException, NdexException
    {
        final Group newGroup = new Group();
        newGroup.setDescription("This is a test group.");
        newGroup.setOrganizationName("Unit Tested Group");
        newGroup.setWebsite("http://www.ndexbio.org");
        
        _groupService.createGroup(null);
    }
    
    @Test
    public void deleteGroup()
    {
        //Assert.assertTrue(createNewGroup());

        final ORID testGroupRid = getRid("Test Group");
        Assert.assertTrue(deleteTargetGroup(IdConverter.toJid(testGroupRid)));
    }

    @Test(expected = IllegalArgumentException.class)
    public void deleteGroupInvalid() throws IllegalArgumentException, ObjectNotFoundException, SecurityException, NdexException
    {
        _groupService.deleteGroup("");
    }

    @Test(expected = ObjectNotFoundException.class)
    public void deleteGroupNonexistant() throws IllegalArgumentException, ObjectNotFoundException, SecurityException, NdexException
    {
        _groupService.deleteGroup("C999R999");
    }

    @Test
    public void findGroups()
    {
        final SearchParameters searchParameters = new SearchParameters();
        searchParameters.setSearchString("triptychjs");
        searchParameters.setSkip(0);
        searchParameters.setTop(25);
        
        try
        {
            _groupService.findGroups(searchParameters, "contains");
        }
        catch (Exception e)
        {
            Assert.fail(e.getMessage());
            e.printStackTrace();
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void findGroupsInvalid() throws IllegalArgumentException, NdexException
    {
        final SearchParameters searchParameters = new SearchParameters();
        searchParameters.setSearchString("");
        searchParameters.setSkip(0);
        searchParameters.setTop(25);
        
        _groupService.findGroups(null, null);
    }

    @Test
    public void getGroupById()
    {
        try
        {
            final ORID groupRid = getRid("triptychjs");
            final Group testGroup = _groupService.getGroup(IdConverter.toJid(groupRid));
            Assert.assertNotNull(testGroup);
        }
        catch (Exception e)
        {
            Assert.fail(e.getMessage());
            e.printStackTrace();
        }
    }

    @Test
    public void getGroupByName()
    {
        try
        {
            final Group testGroup = _groupService.getGroup("triptychjs");
            Assert.assertNotNull(testGroup);
        }
        catch (Exception e)
        {
            Assert.fail(e.getMessage());
            e.printStackTrace();
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void getGroupInvalid() throws IllegalArgumentException, NdexException
    {
        _groupService.getGroup("");
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void removeMemberInvalidGroup() throws IllegalArgumentException, ObjectNotFoundException, SecurityException, NdexException
    {
        final ORID userId = getRid("dexterpratt");

        _groupService.removeMember("", IdConverter.toJid(userId));
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void removeMemberInvalidUserId() throws IllegalArgumentException, ObjectNotFoundException, SecurityException, NdexException
    {
        final ORID testGroupRid = getRid("triptychjs");

        _groupService.removeMember(IdConverter.toJid(testGroupRid), "");
    }
    
    @Test(expected = ObjectNotFoundException.class)
    public void removeMemberNonexistantGroup() throws IllegalArgumentException, ObjectNotFoundException, SecurityException, NdexException
    {
        final ORID userId = getRid("dexterpratt");

        _groupService.removeMember("C999R999", IdConverter.toJid(userId));
    }
    
    @Test(expected = Exception.class)
    public void removeMemberNonexistantUser() throws IllegalArgumentException, ObjectNotFoundException, SecurityException, NdexException
    {
        final ORID testGroupRid = getRid("triptychjs");

        _groupService.removeMember(IdConverter.toJid(testGroupRid), "C999R999");
    }
    
    @Test(expected = Exception.class)
    public void removeMemberOnlyAdminMember() throws IllegalArgumentException, ObjectNotFoundException, SecurityException, NdexException
    {
        final ORID testGroupRid = getRid("triptychjs");
        final ORID userId = getRid("dexterpratt");

        _groupService.removeMember(IdConverter.toJid(testGroupRid), IdConverter.toJid(userId));
    }

    @Test
    public void updateGroup()
    {
        try
        {
            Assert.assertTrue(createNewGroup());
            
            //Refresh the user or the system won't know they have access to
            //update the group
            this.resetLoggedInUser();
            this.setLoggedInUser();
            
            final ORID testGroupRid = getRid("Test Group");
            final Group testGroup = _groupService.getGroup(IdConverter.toJid(testGroupRid));

            testGroup.setName("Updated Test Group");
            _groupService.updateGroup(testGroup);
            Assert.assertEquals(_groupService.getGroup(testGroup.getId()).getName(), testGroup.getName());

            Assert.assertTrue(deleteTargetGroup(testGroup.getId()));
        }
        catch (Exception e)
        {
            Assert.fail(e.getMessage());
            e.printStackTrace();
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void updateGroupInvalid() throws IllegalArgumentException, ObjectNotFoundException, SecurityException, NdexException
    {
        _groupService.updateGroup(null);
    }

    @Test(expected = ObjectNotFoundException.class)
    public void updateGroupNonexistant() throws IllegalArgumentException, ObjectNotFoundException, SecurityException, NdexException
    {
        final Group updatedGroup = new Group();
        updatedGroup.setId("C999R999");
        updatedGroup.setDescription("This is a test group.");
        updatedGroup.setName("Test Group");
        updatedGroup.setOrganizationName("Unit Tested Group");
        updatedGroup.setWebsite("http://www.ndexbio.org");

        _groupService.updateGroup(updatedGroup);
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void updateMemberInvalidGroup() throws IllegalArgumentException, ObjectNotFoundException, SecurityException, NdexException
    {
        final ORID testUserId = getRid("dexterpratt");
        
        final Membership testMembership = new Membership();
        testMembership.setPermissions(Permissions.READ);
        testMembership.setResourceId(IdConverter.toJid(testUserId));
        testMembership.setResourceName("dexterpratt");

        _groupService.updateMember("", testMembership);
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void updateMemberInvalidMembership() throws IllegalArgumentException, ObjectNotFoundException, SecurityException, NdexException
    {
        final ORID testGroupRid = getRid("triptychjs");
        
        _groupService.updateMember(IdConverter.toJid(testGroupRid), null);
    }
    
    @Test(expected = Exception.class)
    public void updateMemberInvalidUserId() throws IllegalArgumentException, ObjectNotFoundException, SecurityException, NdexException
    {
        final ORID testGroupRid = getRid("triptychjs");
        
        final Membership testMembership = new Membership();
        testMembership.setPermissions(Permissions.READ);
        testMembership.setResourceId("C999R999");
        testMembership.setResourceName("dexterpratt");

        _groupService.updateMember(IdConverter.toJid(testGroupRid), testMembership);
    }
    
    @Test(expected = ObjectNotFoundException.class)
    public void updateMemberNonexistantGroup() throws IllegalArgumentException, ObjectNotFoundException, SecurityException, NdexException
    {
        final ORID testUserId = getRid("dexterpratt");
        
        final Membership testMembership = new Membership();
        testMembership.setPermissions(Permissions.READ);
        testMembership.setResourceId(IdConverter.toJid(testUserId));
        testMembership.setResourceName("dexterpratt");

        _groupService.updateMember("C999R999", testMembership);
    }
    
    @Test(expected = Exception.class)
    public void updateMemberNonexistantUser() throws IllegalArgumentException, ObjectNotFoundException, SecurityException, NdexException
    {
        final ORID testGroupRid = getRid("triptychjs");
        
        final Membership testMembership = new Membership();
        testMembership.setPermissions(Permissions.READ);
        testMembership.setResourceId("C999R999");
        testMembership.setResourceName("dexterpratt");

        _groupService.updateMember(IdConverter.toJid(testGroupRid), testMembership);
    }
    
    @Test(expected = Exception.class)
    public void updateMemberOnlyAdminMember() throws IllegalArgumentException, ObjectNotFoundException, SecurityException, NdexException
    {
        final ORID testGroupRid = getRid("triptychjs");
        final ORID testUserId = getRid("dexterpratt");
        
        final Membership testMembership = new Membership();
        testMembership.setPermissions(Permissions.READ);
        testMembership.setResourceId(IdConverter.toJid(testUserId));
        testMembership.setResourceName("dexterpratt");

        _groupService.updateMember(IdConverter.toJid(testGroupRid), testMembership);
    }


    
    
    private boolean createNewGroup()
    {
        final Group newGroup = new Group();
        newGroup.setDescription("This is a test group.");
        newGroup.setName("Test Group");
        newGroup.setOrganizationName("Unit Tested Group");
        newGroup.setWebsite("http://www.ndexbio.org");

        try
        {
            final Group createdGroup = _groupService.createGroup(newGroup);
            Assert.assertNotNull(createdGroup);

            return true;
        }
        catch (DuplicateObjectException doe)
        {
            return true;
        }
        catch (Exception e)
        {
            Assert.fail(e.getMessage());
            e.printStackTrace();
        }

        return false;
    }
    
    private boolean deleteTargetGroup(String groupId)
    {
        try
        {
            _groupService.deleteGroup(groupId);
            Assert.assertNull(_groupService.getGroup(groupId));
            
            return true;
        }
        catch (Exception e)
        {
            Assert.fail(e.getMessage());
            e.printStackTrace();
        }
        
        return false;
    }
    */
}