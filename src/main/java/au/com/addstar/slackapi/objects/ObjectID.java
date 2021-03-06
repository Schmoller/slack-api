package au.com.addstar.slackapi.objects;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@EqualsAndHashCode
public class ObjectID
{
    private ObjectType type;
    private String id;

    public ObjectID(String full)
    {
        if (full.isEmpty())
        {
            id = "";
            type = ObjectType.Unknown;
            return;
        }

        char classifier = Character.toUpperCase(full.charAt(0));
        this.type = ObjectType.Unknown;

        for (ObjectType type : ObjectType.values())
        {
            if (type.getClassifier() == classifier)
            {
                this.type = type;
                break;
            }
        }

        id = full.substring(1);
    }

    @Override
    public String toString()
    {
        return String.format("%s%s", type.getClassifier(), id);
    }

    @Getter
    @RequiredArgsConstructor
    private enum ObjectType
    {
        User('U', au.com.addstar.slackapi.objects.User.class),
        Conversation('C', au.com.addstar.slackapi.objects.Conversation.class),
        GroupConversation('G', au.com.addstar.slackapi.objects.Conversation.class),
        DirectConversation('D', au.com.addstar.slackapi.objects.Conversation.class),
        Team('T', null),
        Bot('B', null),
        Unknown('\0', null);

        private final char classifier;
        private final Class<?> typeClass;
    }
}
